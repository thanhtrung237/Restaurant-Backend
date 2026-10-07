package com.group.restaurant.services;

import com.group.restaurant.dtos.request.tableSession.TableSessionCancelReservationRequest;
import com.group.restaurant.dtos.request.tableSession.TableSessionConfirmOpenRequest;
import com.group.restaurant.dtos.request.tableSession.TableSessionCreateRequest;
import com.group.restaurant.dtos.request.tableSession.TableSessionOpenRequest;
import com.group.restaurant.dtos.request.tableSession.TableSessionReservationRequest;
import com.group.restaurant.dtos.request.tableSession.TableSessionUpdateRequest;
import com.group.restaurant.dtos.response.tableSession.TableSessionResponse;

import java.util.List;

public interface TableSessionService {

    // Quét QR → tạo phiên WAITING_FOR_OPEN (public)
    TableSessionResponse createSession(TableSessionCreateRequest request);

    // Đặt bàn trước → tạo phiên RESERVED (FR-TBL-03, chỉ Phục vụ / QL chi nhánh)
    TableSessionResponse reserveSession(TableSessionReservationRequest request);

    // Phục vụ xác nhận mở bàn → SERVING (áp dụng cho cả WAITING_FOR_OPEN và RESERVED)
    TableSessionResponse confirmOpen(Long id, TableSessionConfirmOpenRequest request);

    // Hủy đặt bàn trước RESERVED → CANCELLED
    TableSessionResponse cancelReservation(Long id, TableSessionCancelReservationRequest request);

    // Yêu cầu thanh toán → WAITING_FOR_PAY
    TableSessionResponse requestPayment(Long id);

    // QL chi nhánh mở lại bàn WAITING_FOR_PAY → SERVING
    TableSessionResponse reopenSession(Long id);

    TableSessionResponse openSession(TableSessionOpenRequest request);

    TableSessionResponse updateSession(Long id, TableSessionUpdateRequest request);

    TableSessionResponse getSessionById(Long id);

    TableSessionResponse getOpenSessionByTable(Long tableId);

    List<TableSessionResponse> getOpenSessionsByBranch(Long branchId);

    List<TableSessionResponse> getSessionsByTable(Long tableId);

    TableSessionResponse closeSession(Long id);

    TableSessionResponse cancelSession(Long id, String reason);

    void deleteSession(Long id);

    // Khách gọi nhân viên hỗ trợ tại bàn (FR-CUS-07)
    // orderDetailId: ID dòng món cụ thể nếu khách báo từ món đã vào bếp, null nếu gọi nhân viên chung
    void callStaff(Long id, Long orderDetailId);
}
