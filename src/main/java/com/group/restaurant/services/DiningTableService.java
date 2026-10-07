package com.group.restaurant.services;

import com.group.restaurant.dtos.request.diningTable.DiningTableAddRequest;
import com.group.restaurant.dtos.request.diningTable.DiningTableUpdateRequest;
import com.group.restaurant.dtos.response.diningTable.DiningTableResponse;
import com.group.restaurant.dtos.response.diningTable.QrScanResponse;
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

    /**
        Sinh URL thật cho QR code của bàn và lưu vào DB.
     * @param tableId  ID bàn cần sinh QR
     * @param frontendBaseUrl base URL của FE (VD: http://localhost:3000)
     * @return URL đã lưu vào qrCode field
     */
    String generateQrCode(Long tableId, String frontendBaseUrl);

    /**
     * Lấy đường dẫn URL của QR code bàn (để FE tự sinh ảnh QR).
     * @param tableId ID bàn
     * @param frontendBaseUrl base URL của FE (VD: http://localhost:5173)
     * @return URL của bàn (VD: http://localhost:5173/ban/QR-B1-T01)
     */
    String getQrUrl(Long tableId, String frontendBaseUrl);

    /**
     * Trả về ảnh PNG của QR code bàn (dùng ZXing).
     * Endpoint: GET /tables/{id}/qr-image
     *
     * @param tableId ID bàn
     * @param size    kích thước ảnh pixel (mặc định 300)
     * @return mảng byte PNG
     */
    byte[] generateQrImage(Long tableId, int size);

    /**
        Xử lý khi khách quét QR bàn.
     * Tự động phân loại và xử lý tất cả trường hợp theo đặc tả:
     *   - Bàn chưa có phiên → tạo phiên WAITING_FOR_OPEN
     *   - Bàn đang SERVING hoặc WAITING_FOR_OPEN → trả về phiên hiện tại
     *   - Bàn RESERVED → báo lỗi
     *   - Chi nhánh inactive → báo lỗi
     *
     * @param tableId ID bàn (FE đọc từ URL trong QR)
     * @return QrScanResponse với scanAction để FE biết làm gì tiếp
     */
    QrScanResponse scanQr(Long tableId);

    // === NGHIỆP VỤ ĐẶT BÀN TRƯỚC   ===
    DiningTableResponse reserveTable(Long tableId, DiningTableReservationRequest request);

    void cancelReservation(Long tableId);

    void changeStatus(Long id, DiningTableStatus status);

    void deleteTable(Long id);
}
