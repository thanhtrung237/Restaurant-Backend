package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.diningTable.DiningTableAddRequest;
import com.group.restaurant.dtos.request.diningTable.DiningTableReservationRequest;
import com.group.restaurant.dtos.request.diningTable.DiningTableUpdateRequest;
import com.group.restaurant.dtos.response.diningTable.DiningTableResponse;
import com.group.restaurant.dtos.response.diningTable.QrScanResponse;
import com.group.restaurant.enums.BranchStatus;
import com.group.restaurant.enums.DiningTableStatus;
import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.InvalidParamException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.mappers.DiningTableMapper;
import com.group.restaurant.models.Area;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.DiningTable;
import com.group.restaurant.models.TableSession;
import com.group.restaurant.repositories.AreaRepository;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.repositories.DiningTableRepository;
import com.group.restaurant.repositories.TableSessionRepository;
import com.group.restaurant.services.DiningTableService;
import com.group.restaurant.utils.TenantGuard;
import com.group.restaurant.enums.OperationalPermission;
import com.group.restaurant.services.OperationalPermissionService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DiningTableServiceImpl implements DiningTableService {

    private final DiningTableRepository diningTableRepository;
    private final BranchRepository branchRepository;
    private final AreaRepository areaRepository;
    private final TableSessionRepository tableSessionRepository;
    private final DiningTableMapper diningTableMapper;
    private final TenantGuard tenantGuard;
    private final OperationalPermissionService permissions;

    @Override
    public DiningTableResponse createTable(DiningTableAddRequest request) {
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy chi nhánh với ID: " + request.getBranchId()));
        assertTableNameNotRetired(branch.getId(), request.getName());

        Area area = null;
        if (request.getAreaId() != null) {
            area = findAreaInBranch(request.getAreaId(), branch.getId());
        }

        if (request.getQrCode() != null && !request.getQrCode().isBlank() &&
                diningTableRepository.findByBranchIdAndQrCode(request.getBranchId(), request.getQrCode()).isPresent()) {
            throw new ConflictException("Mã QR '" + request.getQrCode() + "' đã tồn tại trong chi nhánh này");
        }

        DiningTable table = diningTableMapper.toEntity(request);
        table.setBranch(branch);
        table.setArea(area);
        table.setStatus(DiningTableStatus.AVAILABLE);

        DiningTable savedTable = diningTableRepository.save(table);
        return buildDiningTableResponse(savedTable);
    }

    @Override
    public DiningTableResponse updateTable(Long id, DiningTableUpdateRequest request) {
        DiningTable table = findTableForWrite(id);
        tenantGuard.assertBranchAssigned(table.getBranch().getId(), TenantGuard.ROLE_BRANCH_MANAGER);
        if (request.getName() != null && !request.getName().equals(table.getName())) {
            assertTableNameNotRetired(table.getBranch().getId(), request.getName());
        }

        if (request.isClearArea()) {
            if (request.getAreaId() != null) {
                throw new InvalidParamException("Chỉ chọn một cách cập nhật khu vực");
            }
            table.setArea(null);
        } else if (request.getAreaId() != null) {
            table.setArea(findAreaInBranch(request.getAreaId(), table.getBranch().getId()));
        }

        if (request.getQrCode() != null && !request.getQrCode().isBlank() &&
                !request.getQrCode().equals(table.getQrCode())) {
            if (diningTableRepository.findByBranchIdAndQrCode(table.getBranch().getId(), request.getQrCode())
                    .isPresent()) {
                throw new ConflictException("Mã QR '" + request.getQrCode() + "' đã tồn tại trong chi nhánh này");
            }
        }

        diningTableMapper.updateEntity(table, request);
        DiningTable updatedTable = diningTableRepository.save(table);
        return buildDiningTableResponse(updatedTable);
    }

    private Area findAreaInBranch(Long areaId, Long branchId) {
        return areaRepository.findForTableAssignment(areaId)
                .filter(area -> branchId.equals(area.getBranch().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khu vực trong chi nhánh của bàn"));
    }

    @Override
    @Transactional(readOnly = true)
    public DiningTableResponse getTableById(Long id) {
        DiningTable table = findTableById(id);
        return buildDiningTableResponse(table);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiningTableResponse> getTablesByBranch(Long branchId, DiningTableStatus status) {
        if (!branchRepository.existsById(branchId)) {
            throw new ResourceNotFoundException("Không tìm thấy chi nhánh với ID: " + branchId);
        }

        List<DiningTable> tables = (status != null)
                ? diningTableRepository.findByBranchIdAndStatus(branchId, status)
                : diningTableRepository.findByBranchId(branchId);

        return tables.stream()
                .map(this::buildDiningTableResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiningTableResponse> getTablesByArea(Long areaId) {
        if (!areaRepository.existsById(areaId)) {
            throw new ResourceNotFoundException("Không tìm thấy khu vực với ID: " + areaId);
        }

        List<DiningTable> tables = diningTableRepository.findByAreaId(areaId);
        return tables.stream()
                .map(this::buildDiningTableResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DiningTableResponse getTableByQrCode(Long branchId, String qrCode) {
        DiningTable table = diningTableRepository.findByBranchIdAndQrCode(branchId, qrCode)
                .orElseGet(() -> {
                    // Fallback 1: Thay khoảng trắng bằng gạch nối (VD: "QR B1 T01" -> "QR-B1-T01")
                    String dashed = qrCode.trim().replaceAll("\\s+", "-");
                    Optional<DiningTable> dashedOpt = diningTableRepository.findByBranchIdAndQrCode(branchId, dashed);
                    if (dashedOpt.isPresent()) return dashedOpt.get();

                    // Fallback 2: Thử tìm theo tên bàn hoặc URL kết thúc bằng qrCode
                    List<DiningTable> tables = diningTableRepository.findByBranchId(branchId);
                    return tables.stream()
                            .filter(t -> (t.getQrCode() != null && (t.getQrCode().equalsIgnoreCase(dashed)
                                    || t.getQrCode().equalsIgnoreCase(qrCode)
                                    || t.getQrCode().endsWith("/" + qrCode)
                                    || t.getQrCode().endsWith("-" + dashed)))
                                    || t.getName().equalsIgnoreCase(qrCode)
                                    || t.getName().equalsIgnoreCase("Bàn " + qrCode)
                                    || String.valueOf(t.getId()).equals(qrCode)
                                    || t.getName().replaceAll("\\s+", "").equalsIgnoreCase(qrCode.replaceAll("\\s+", "")))
                            .findFirst()
                            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bàn với mã QR: " + qrCode));
                });
        return buildDiningTableResponse(table);
    }

    @Override
    public void changeStatus(Long id, DiningTableStatus status) {
        DiningTable table = findTableForWrite(id);
        tenantGuard.assertBranchAssigned(table.getBranch().getId(), TenantGuard.ROLE_BRANCH_MANAGER);
        if (status == DiningTableStatus.RESERVED || table.getStatus() == DiningTableStatus.RESERVED) {
            throw new ConflictException("Hãy dùng thao tác đặt bàn hoặc hủy đặt bàn.");
        }
        if (status == DiningTableStatus.INACTIVE || table.getStatus() == DiningTableStatus.INACTIVE) {
            assertTableIdle(table);
        }
        table.setStatus(status);
        diningTableRepository.save(table);
    }

    @Override
    public void deleteTable(Long id) {
        DiningTable table = findTableForWrite(id);
        tenantGuard.assertBranchAssigned(table.getBranch().getId(), TenantGuard.ROLE_BRANCH_MANAGER);
        assertTableIdle(table);
        if (tableSessionRepository.countHistoryByTableId(id) > 0) {
            throw new ConflictException("Bàn đã có lịch sử. Hãy chọn Ngừng sử dụng để giữ dữ liệu.");
        }
        table.softDelete(null);
        diningTableRepository.save(table);
    }

    private void assertTableIdle(DiningTable table) {
        if (table.getStatus() == DiningTableStatus.OCCUPIED || table.getStatus() == DiningTableStatus.RESERVED
                || tableSessionRepository.countActiveSessionsByTableId(table.getId()) > 0) {
            throw new ConflictException("Bàn còn phiên hoạt động hoặc lịch đặt trước. Hãy xử lý phiên trước khi tiếp tục.");
        }
    }

    private void assertTableNameNotRetired(Long branchId, String name) {
        if (diningTableRepository.countDeletedWithName(branchId, name) > 0) {
            throw new ConflictException("Tên bàn đã từng được dùng. Hãy chọn tên khác để tránh trùng với mã QR cũ.");
        }
    }

    private DiningTable findTableForWrite(Long id) {
        return diningTableRepository.findForGuestAdmission(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bàn với ID: " + id));
    }

    private DiningTable findTableById(Long id) {
        return diningTableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bàn với ID: " + id));
    }

    private DiningTableResponse buildDiningTableResponse(DiningTable table) {
        DiningTableResponse response = diningTableMapper.toResponse(table);
        boolean hasOpenSession = tableSessionRepository.countActiveSessionsByTableId(table.getId()) > 0;
        response.setHasOpenSession(hasOpenSession);
        return response;
    }

    @Override
    public DiningTableResponse reserveTable(Long tableId, DiningTableReservationRequest request) {
        DiningTable table = findTableForWrite(tableId);
        permissions.require(table.getBranch().getId(), OperationalPermission.RESERVE_TABLE);

        // 1. Bàn phải ở trạng thái AVAILABLE mới được đặt trước (BR-29)
        if (table.getStatus() != DiningTableStatus.AVAILABLE) {
            throw new ConflictException("Bàn đang không ở trạng thái trống, không thể thực hiện đặt trước");
        }

        // 2. Bàn không được đang có phiên phục vụ hoạt động
        boolean hasActiveSession = tableSessionRepository.countActiveSessionsByTableId(tableId) > 0;
        if (hasActiveSession) {
            throw new ConflictException("Bàn đang có phiên phục vụ hoạt động, không thể thực hiện đặt trước");
        }

        // 3. Đổi trạng thái Bàn sang RESERVED (3)
        table.setStatus(DiningTableStatus.RESERVED);
        DiningTable updatedTable = diningTableRepository.save(table);

        // 4. Tạo bản ghi Phiên bàn (TableSession) ghi nhận thông tin đặt bàn
        TableSession reservationSession = TableSession.builder()
                .table(table)
                .status(TableSessionStatus.RESERVED)
                .branch(table.getBranch())
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .guestCount(request.getGuestCount())
                .reservationTime(request.getReservationTime())
                .note(request.getNote())
                .build();
        tableSessionRepository.save(reservationSession);

        return buildDiningTableResponse(updatedTable);
    }

    // =========================================================================
    // CÁC HÀM XỬ LÝ PHỤ TRỢ DỰ ÁN
    // =========================================================================

    @Override
    public String generateQrCode(Long tableId, String frontendBaseUrl) {
        DiningTable table = findTableForWrite(tableId);
        if (table.getStatus() == DiningTableStatus.INACTIVE) {
            throw new ConflictException("Bàn đã ngừng sử dụng.");
        }

        String baseUrl = (frontendBaseUrl != null && !frontendBaseUrl.isBlank())
                ? frontendBaseUrl.replaceAll("/$", "")
                : "http://localhost:5173";

        // Sinh mã định danh sạch (không phải URL) — VD: "QR-B1-Bàn-01"
        // Chỉ sinh lại nếu chưa có hoặc đang lưu URL cũ từ cơ chế cũ
        if (table.getQrCode() == null || table.getQrCode().isBlank()
                || table.getQrCode().startsWith("http")) {
            String cleanName = table.getName().trim()
                    .replaceAll("[^\\w\\dÀ-ỹà-ỹ\\s-]", "")
                    .replaceAll("\\s+", "-");
            String code = "QR-B" + table.getBranch().getId() + "-" + cleanName;
            table.setQrCode(code);
            diningTableRepository.save(table);
        }

        // Trả về URL hoàn chỉnh để FE biết nội dung cần mã hoá vào QR code
        // FE sẽ dùng thư viện (qrcode.react) để tự vẽ ảnh QR từ URL này
        String customerUrl = baseUrl + "/ban/" + table.getQrCode();
        log.info("[FR-TBL-02] Mã QR bàn #{} ({}): code={}, url={}",
                table.getId(), table.getName(), table.getQrCode(), customerUrl);
        return customerUrl;
    }

    @Override
    @Transactional
    public String getQrUrl(Long tableId, String frontendBaseUrl) {
        return generateQrCode(tableId, frontendBaseUrl);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateQrImage(Long tableId, int size) {
        DiningTable table = findTableById(tableId);

        String qrContent = table.getQrCode();
        if (qrContent == null || qrContent.isBlank()) {
            throw new InvalidParamException(
                "Bàn chưa có mã QR. Vui lòng gọi POST /tables/" + tableId + "/generate-qr trước.");
        }

        try {
            int imageSize = (size > 0) ? size : 300;

            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            // QUAN TRỌNG: Khi khách dùng camera điện thoại quét, nội dung QR phải là URL
            // để điện thoại nhận diện và tự động mở trình duyệt tới trang chọn số lượng khách
            String urlToEncode;
            if (qrContent.startsWith("http://") || qrContent.startsWith("https://")) {
                urlToEncode = qrContent;
            } else {
                urlToEncode = "http://localhost:5173/ban/" + qrContent;
            }

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(urlToEncode, BarcodeFormat.QR_CODE, imageSize, imageSize, hints);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();

        } catch (WriterException | IOException e) {
            log.error("[FR-TBL-02] Lỗi sinh ảnh QR cho bàn #{}: {}", tableId, e.getMessage());
            throw new RuntimeException("Không thể sinh ảnh QR: " + e.getMessage(), e);
        }
    }

    /**
    Xử lý quét QR bàn — logic trung tâm của luồng khách.
     *
     * Các trường hợp theo đặc tả:
     * 1. Chi nhánh ngưng → BRANCH_INACTIVE
     * 2. Bàn có phiên RESERVED → RESERVED (chặn, khách gặp nhân viên)
     * 3. Bàn có phiên WAITING_FOR_OPEN hoặc SERVING → JOIN_EXISTING (tham gia)
     * 4. Bàn chưa có phiên → tạo phiên WAITING_FOR_OPEN → WAITING_FOR_STAFF
     */
    @Override
    public QrScanResponse scanQr(Long tableId) {
        DiningTable table = findTableForWrite(tableId);
        if (table.getStatus() == DiningTableStatus.INACTIVE) {
            throw new ConflictException("Bàn đã ngừng sử dụng.");
        }
        Branch branch = table.getBranch();

        // ── TH1: Chi nhánh ngưng hoạt động ─────────────────────────────────
        if (branch.getStatus() != BranchStatus.ACTIVE) {
            return QrScanResponse.builder()
                    .tableId(table.getId())
                    .tableName(table.getName())
                    .branchId(branch.getId())
                    .branchName(branch.getName())
                    .tableStatus(table.getStatus())
                    .scanAction(QrScanResponse.ScanAction.BRANCH_INACTIVE)
                    .message("Chi nhánh " + branch.getName() + " tạm ngưng hoạt động. Vui lòng liên hệ nhân viên.")
                    .build();
        }

        // ── Kiểm tra phiên active của bàn ───────────────────────────────────
        Optional<TableSession> activeSession = tableSessionRepository.findActiveSessionByTableId(table.getId());

        if (activeSession.isPresent()) {
            TableSession session = activeSession.get();

            // ── TH2: Bàn đang RESERVED → không cho quét ─────────────────────
            if (session.getStatus() == TableSessionStatus.RESERVED) {
                return QrScanResponse.builder()
                        .tableId(table.getId())
                        .tableName(table.getName())
                        .tableCapacity(table.getCapacity())
                        .branchId(branch.getId())
                        .branchName(branch.getName())
                        .branchAddress(branch.getAddress())
                        .sessionId(session.getId())
                        .sessionStatus(session.getStatus())
                        .tableStatus(table.getStatus())
                        .scanAction(QrScanResponse.ScanAction.RESERVED)
                        .message("Bàn " + table.getName() + " đã được đặt trước. Vui lòng gặp nhân viên để được hỗ trợ.")
                        .build();
            }

            // ── TH3: Bàn đang WAITING_FOR_OPEN hoặc SERVING → tham gia ─────
            if (session.getStatus() == TableSessionStatus.WAITING_FOR_OPEN
                    || session.getStatus() == TableSessionStatus.SERVING
                    || session.getStatus() == TableSessionStatus.WAITING_FOR_PAY) {

                String message = switch (session.getStatus()) {
                    case WAITING_FOR_OPEN -> "Đang chờ nhân viên xác nhận mở bàn...";
                    case SERVING          -> "Chào mừng! Bàn " + table.getName() + " đang phục vụ.";
                    case WAITING_FOR_PAY  -> "Bàn đang chờ thanh toán.";
                    default               -> "Tham gia phiên hiện tại.";
                };

                return QrScanResponse.builder()
                        .tableId(table.getId())
                        .tableName(table.getName())
                        .tableCapacity(table.getCapacity())
                        .branchId(branch.getId())
                        .branchName(branch.getName())
                        .branchAddress(branch.getAddress())
                        .sessionId(session.getId())
                        .sessionStatus(session.getStatus())
                        .tableStatus(table.getStatus())
                        .scanAction(QrScanResponse.ScanAction.JOIN_EXISTING)
                        .message(message)
                        .build();
            }
        }

        // ── TH4: Bàn chưa có phiên → tạo phiên WAITING_FOR_OPEN ────────────
        TableSession newSession = TableSession.builder()
                .table(table)
                .branch(branch)
                .status(TableSessionStatus.WAITING_FOR_OPEN)
                .build();

        TableSession saved = tableSessionRepository.save(newSession);

        // Cập nhật trạng thái bàn → OCCUPIED
        table.setStatus(DiningTableStatus.OCCUPIED);
        diningTableRepository.save(table);

        log.info("[FR-CUS-01] Quét QR bàn #{} ({}) → Tạo phiên #{} WAITING_FOR_OPEN",
                table.getId(), table.getName(), saved.getId());

        return QrScanResponse.builder()
                .tableId(table.getId())
                .tableName(table.getName())
                .tableCapacity(table.getCapacity())
                .branchId(branch.getId())
                .branchName(branch.getName())
                .branchAddress(branch.getAddress())
                .sessionId(saved.getId())
                .sessionStatus(saved.getStatus())
                .tableStatus(table.getStatus())
                .scanAction(QrScanResponse.ScanAction.WAITING_FOR_STAFF)
                .message("Đang chờ nhân viên xác nhận mở bàn " + table.getName() + ". Vui lòng chờ trong giây lát.")
                .build();
    }

    @Override
    public void cancelReservation(Long tableId) {
        DiningTable table = findTableForWrite(tableId);
        permissions.require(table.getBranch().getId(), OperationalPermission.RESERVE_TABLE);

        if (table.getStatus() != DiningTableStatus.RESERVED) {
            throw new ConflictException("Bàn không ở trạng thái đặt trước để thực hiện hủy");
        }

        table.setStatus(DiningTableStatus.AVAILABLE);
        diningTableRepository.save(table);

        tableSessionRepository.findActiveSessionByTableIdAndStatus(tableId, TableSessionStatus.RESERVED)
                .ifPresent(session -> {
                    session.setStatus(TableSessionStatus.CANCELLED);
                    tableSessionRepository.save(session);
                });
    }
}
