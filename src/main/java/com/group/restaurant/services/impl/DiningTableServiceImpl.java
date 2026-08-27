package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.request.diningTable.DiningTableAddRequest;
import com.group.restaurant.dtos.request.diningTable.DiningTableUpdateRequest;
import com.group.restaurant.dtos.response.diningTable.DiningTableResponse;
import com.group.restaurant.enums.DiningTableStatus;
import com.group.restaurant.enums.TableSessionStatus;
import com.group.restaurant.exceptions.ConflictException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.group.restaurant.dtos.request.diningTable.DiningTableReservationRequest;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DiningTableServiceImpl implements DiningTableService {

    private final DiningTableRepository diningTableRepository;
    private final BranchRepository branchRepository;
    private final AreaRepository areaRepository;
    private final TableSessionRepository tableSessionRepository;
    private final DiningTableMapper diningTableMapper;

    @Override
    public DiningTableResponse createTable(DiningTableAddRequest request) {
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy chi nhánh với ID: " + request.getBranchId()));

        Area area = null;
        if (request.getAreaId() != null) {
            area = areaRepository.findById(request.getAreaId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy khu vực với ID: " + request.getAreaId()));
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
        DiningTable table = findTableById(id);

        if (request.getAreaId() != null) {
            Area area = areaRepository.findById(request.getAreaId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy khu vực với ID: " + request.getAreaId()));
            table.setArea(area);
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
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bàn với mã QR: " + qrCode));
        return buildDiningTableResponse(table);
    }

    @Override
    public void changeStatus(Long id, DiningTableStatus status) {
        DiningTable table = findTableById(id);
        table.setStatus(status);
        diningTableRepository.save(table);
    }

    @Override
    public void deleteTable(Long id) {
        DiningTable table = findTableById(id);
        table.softDelete(null);
        diningTableRepository.save(table);
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
        DiningTable table = findTableById(tableId);

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
    public String generateQrCode(Long tableId) {
        DiningTable table = findTableById(tableId);
        String qrData = "BRANCH_" + table.getBranch().getId() + "_TABLE_" + table.getId();
        table.setQrCode(qrData);
        diningTableRepository.save(table);
        return qrData;
    }

    @Override
    public void cancelReservation(Long tableId) {
        DiningTable table = findTableById(tableId);

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
