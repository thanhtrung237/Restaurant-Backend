package com.group.restaurant.services;

import com.group.restaurant.dtos.request.diningTable.DiningTableAddRequest;
import com.group.restaurant.dtos.request.diningTable.DiningTableUpdateRequest;
import com.group.restaurant.dtos.response.diningTable.DiningTableResponse;
import com.group.restaurant.enums.DiningTableStatus;
import com.group.restaurant.dtos.request.diningTable.DiningTableReservationRequest;
import java.util.List;

public interface DiningTableService {

    DiningTableResponse createTable(DiningTableAddRequest request);

    DiningTableResponse updateTable(Long id, DiningTableUpdateRequest request);

    DiningTableResponse getTableById(Long id);

    List<DiningTableResponse> getTablesByBranch(Long branchId, DiningTableStatus status);

    List<DiningTableResponse> getTablesByArea(Long areaId);

    DiningTableResponse getTableByQrCode(Long branchId, String qrCode);

    String generateQrCode(Long tableId); // Tạo/xuất mã QR cho bàn

    // === 3. NGHIỆP VỤ ĐẶT BÀN TRƯỚC (FR-TBL-03, BR-29) ===
    DiningTableResponse reserveTable(Long tableId, DiningTableReservationRequest request);

    void cancelReservation(Long tableId);

    void changeStatus(Long id, DiningTableStatus status);

    void deleteTable(Long id);
}
