package com.group.restaurant.services.guest;

import com.group.restaurant.configurations.jwt.AccountUserDetails;
import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.models.TableSession;
import com.group.restaurant.repositories.TableSessionRepository;
import com.group.restaurant.utils.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class GuestSessionGuard {
    private final GuestTokenService tokens;
    private final TableSessionRepository sessions;
    private final TenantGuard tenantGuard;

    @Transactional(readOnly = true)
    public TableSession require(Long id, boolean write) {
        TableSession session = sessions.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy phiên bàn."));
        return authorize(session, write);
    }

    @Transactional
    public TableSession requireForOrderCreation(Long id) {
        TableSession session = sessions.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy phiên bàn."));
        return authorize(session, true);
    }

    private TableSession authorize(TableSession session, boolean write) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AccountUserDetails) {
            tenantGuard.assertBranchAssigned(session.getBranch().getId());
        } else {
            var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            String token = attributes == null ? null : attributes.getRequest().getHeader("X-Guest-Token");
            requireGuest(token, session, write);
        }
        return session;
    }

    public void requireGuest(String token, TableSession session, boolean write) {
        if (session.isTakeaway()) {
            throw new ResponseStatusException(FORBIDDEN, "Đơn mang về được xử lý tại quầy.");
        }
        tokens.verify(token, session);
        if (session.getStatus() == TableSessionStatus.CANCELLED || session.getStatus() == TableSessionStatus.MERGED
                || (write && session.getStatus() == TableSessionStatus.CLOSED)) {
            throw new ResponseStatusException(FORBIDDEN, "Phiên bàn đã kết thúc.");
        }
        // A valid token can read the final receipt until its 24-hour expiry after checkout.
    }
}
