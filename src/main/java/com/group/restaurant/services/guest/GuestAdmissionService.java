package com.group.restaurant.services.guest;

import com.group.restaurant.dtos.request.tableSession.TableSessionCreateRequest;
import com.group.restaurant.dtos.response.tableSession.TableSessionResponse;
import com.group.restaurant.enums.*;
import com.group.restaurant.models.*;
import com.group.restaurant.repositories.*;
import com.group.restaurant.services.TableSessionService;
import com.group.restaurant.utils.TenantGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class GuestAdmissionService {
    public record AccessRequest(@NotNull Long branchId, @NotBlank @Size(max=200) String qrCode,
                                @Min(1) @Max(100) Integer guestCount,
                                @Pattern(regexp="[0-9]{8}") String joinCode, @Valid GuestLocation location) {}
    public record AccessResponse(String token, TableSessionResponse session) {}
    public record TableInfo(Long tableId, Long branchId, String tableName, String branchName,
                            String restaurantName, boolean locationRequired, boolean joinRequired) {}
    private static final SecureRandom RANDOM = new SecureRandom();
    private final DiningTableRepository tables;
    private final TableSessionRepository sessions;
    private final TableSessionService sessionService;
    private final GeofenceService geofence;
    private final GuestTokenService tokens;
    private final GuestRateLimiter limiter;
    private final TenantGuard tenantGuard;

    @Transactional(readOnly=true)
    public TableInfo info(Long branchId, String qrCode) {
        DiningTable table = findTable(branchId, qrCode);
        Branch branch = table.getBranch();
        return new TableInfo(table.getId(), branch.getId(), table.getName(), branch.getName(),
                branch.getRestaurant().getName(), branch.isGeofenceEnabled(),
                sessions.findActiveSessionByTableId(table.getId()).isPresent());
    }

    @Transactional
    public AccessResponse enter(AccessRequest request, String remoteAddress) {
        // Consume before DB writes and before checking QR/code validity. Never trust X-Forwarded-For here.
        limiter.check("admit-ip:" + remoteAddress, 20, 60_000);
        DiningTable table = findTable(request.branchId(), request.qrCode());
        limiter.check("admit-ip-table:" + remoteAddress + ":" + table.getId(), 5, 60_000);
        limiter.check("admit-branch:" + table.getBranch().getId(), 300, 60_000);
        table = tables.findForGuestAdmission(table.getId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        ensureActive(table);
        TableSession existing = sessions.findActiveForGuestAdmission(table.getId()).orElse(null);
        TableSessionResponse response;
        if (existing != null) {
            if (existing.getStatus() == TableSessionStatus.RESERVED) {
                throw new ResponseStatusException(CONFLICT, "Bàn đang được đặt trước. Vui lòng liên hệ nhân viên.");
            }
            // Normal admission uses the same location policy as opening a table.
            // A staff code is only an explicit fallback when location is unavailable.
            boolean staffCodeVerified = false;
            if (request.joinCode() != null) {
                staffCodeVerified = existing.getGuestJoinCode() != null
                        && MessageDigest.isEqual(existing.getGuestJoinCode().getBytes(StandardCharsets.UTF_8),
                        request.joinCode().getBytes(StandardCharsets.UTF_8));
                if (!staffCodeVerified) {
                    throw new ResponseStatusException(FORBIDDEN, "Mã hỗ trợ không đúng. Vui lòng kiểm tra lại với nhân viên tại bàn.");
                }
            }
            geofence.verify(table.getBranch(), request.location(), staffCodeVerified);
            response = sessionService.getSessionById(existing.getId());
        } else {
            geofence.verify(table.getBranch(), request.location(), false);
            TableSessionCreateRequest create = new TableSessionCreateRequest();
            create.setTableId(table.getId());
            create.setGuestCount(request.guestCount() == null ? 2 : request.guestCount());
            response = sessionService.createSession(create);
            existing = sessions.findById(response.getId()).orElseThrow();
        }
        String token = tokens.issue(existing);
        sessions.save(existing);
        return new AccessResponse(token, response);
    }

    @Transactional
    public String staffCode(Long sessionId) {
        TableSession session = sessions.findForGuestCode(sessionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy phiên bàn."));
        tenantGuard.assertBranchAssigned(session.getBranch().getId(), "WAITER", "BRANCH_MANAGER", "MANAGER", "CASHIER");
        if (session.getStatus() != TableSessionStatus.WAITING_FOR_OPEN && session.getStatus() != TableSessionStatus.SERVING
                && session.getStatus() != TableSessionStatus.WAITING_FOR_PAY) {
            throw new ResponseStatusException(CONFLICT, "Chỉ cấp mã cho phiên đang chờ mở, phục vụ hoặc chờ thanh toán.");
        }
        if (session.getGuestJoinCode() == null) {
            session.setGuestJoinCode(String.format("%08d", RANDOM.nextInt(100_000_000)));
            sessions.save(session);
        }
        return session.getGuestJoinCode();
    }

    private DiningTable findTable(Long branchId, String qrCode) {
        return tables.findByBranchIdAndQrCode(branchId, qrCode)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Mã QR không hợp lệ."));
    }
    private void ensureActive(DiningTable table) {
        if (table.getStatus() == DiningTableStatus.INACTIVE
                || table.getBranch().getStatus() != BranchStatus.ACTIVE
                || table.getBranch().getRestaurant().getStatus() != RestaurantStatus.ACTIVE) {
            throw new ResponseStatusException(FORBIDDEN, "Chi nhánh hoặc nhà hàng đang ngừng hoạt động.");
        }
    }
}
