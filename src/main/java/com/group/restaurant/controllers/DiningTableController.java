package com.group.restaurant.controllers;

import com.group.restaurant.dtos.request.diningTable.DiningTableAddRequest;
import com.group.restaurant.dtos.request.diningTable.DiningTableReservationRequest;
import com.group.restaurant.dtos.request.diningTable.DiningTableUpdateRequest;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.diningTable.DiningTableResponse;
import com.group.restaurant.enums.DiningTableStatus;
import com.group.restaurant.services.DiningTableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/tables")
@RequiredArgsConstructor
public class DiningTableController {

        private final DiningTableService diningTableService;

        @PostMapping("")
        public ResponseEntity<ResponseSuccess<DiningTableResponse>> createTable(
                        @Valid @RequestBody DiningTableAddRequest request) {
                return ResponseEntity.status(CREATED).body(new ResponseSuccess<>(
                                CREATED,
                                "Tạo bàn ăn thành công",
                                diningTableService.createTable(request)));
        }

        @PutMapping("/{id}")
        public ResponseEntity<ResponseSuccess<DiningTableResponse>> updateTable(
                        @PathVariable Long id,
                        @Valid @RequestBody DiningTableUpdateRequest request) {
                return ResponseEntity.ok(new ResponseSuccess<>(
                                OK,
                                "Cập nhật thông tin bàn ăn thành công",
                                diningTableService.updateTable(id, request)));
        }

        @GetMapping("/{id}")
        public ResponseEntity<ResponseSuccess<DiningTableResponse>> getTableById(@PathVariable Long id) {
                return ResponseEntity.ok(new ResponseSuccess<>(
                                OK,
                                "Lấy thông tin bàn ăn thành công",
                                diningTableService.getTableById(id)));
        }

        @GetMapping("/branch/{branchId}")
        public ResponseEntity<ResponseSuccess<List<DiningTableResponse>>> getTablesByBranch(
                        @PathVariable Long branchId,
                        @RequestParam(required = false) DiningTableStatus status) {
                return ResponseEntity.ok(new ResponseSuccess<>(
                                OK,
                                "Lấy danh sách bàn theo chi nhánh thành công",
                                diningTableService.getTablesByBranch(branchId, status)));
        }

        @GetMapping("/area/{areaId}")
        public ResponseEntity<ResponseSuccess<List<DiningTableResponse>>> getTablesByArea(
                        @PathVariable Long areaId) {
                return ResponseEntity.ok(new ResponseSuccess<>(
                                OK,
                                "Lấy danh sách bàn theo khu vực thành công",
                                diningTableService.getTablesByArea(areaId)));
        }

        @GetMapping("/branch/{branchId}/qr/{qrCode}")
        public ResponseEntity<ResponseSuccess<DiningTableResponse>> getTableByQrCode(
                        @PathVariable Long branchId,
                        @PathVariable String qrCode) {
                return ResponseEntity.ok(new ResponseSuccess<>(
                                OK,
                                "Lấy thông tin bàn theo QR code thành công",
                                diningTableService.getTableByQrCode(branchId, qrCode)));
        }

        @PutMapping("/{id}/status")
        public ResponseEntity<ResponseSuccess<Void>> changeStatus(
                        @PathVariable Long id,
                        @RequestParam DiningTableStatus status) {
                diningTableService.changeStatus(id, status);
                return ResponseEntity.ok(new ResponseSuccess<>(
                                OK,
                                "Thay đổi trạng thái bàn ăn thành công",
                                null));
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<ResponseSuccess<Void>> deleteTable(@PathVariable Long id) {
                diningTableService.deleteTable(id);
                return ResponseEntity.ok(new ResponseSuccess<>(
                                OK,
                                "Xóa bàn ăn thành công",
                                null));
        }

        @PostMapping("/{id}/generate-qr")
        public ResponseEntity<ResponseSuccess<String>> generateQrCode(@PathVariable Long id) {
                return ResponseEntity.ok(new ResponseSuccess<>(
                                OK,
                                "Khởi tạo mã QR cho bàn thành công",
                                diningTableService.generateQrCode(id)));
        }

        // =========================================================================
        // 3. NGHIỆP VỤ ĐẶT BÀN TRƯỚC (FR-TBL-03 & BR-29)
        // =========================================================================

        @PostMapping("/{id}/reserve")
        public ResponseEntity<ResponseSuccess<DiningTableResponse>> reserveTable(
                        @PathVariable Long id,
                        @Valid @RequestBody DiningTableReservationRequest request) {
                return ResponseEntity.ok(new ResponseSuccess<>(
                                OK,
                                "Đặt bàn trước thành công",
                                diningTableService.reserveTable(id, request)));
        }

        @PutMapping("/{id}/cancel-reservation")
        public ResponseEntity<ResponseSuccess<Void>> cancelReservation(@PathVariable Long id) {
                diningTableService.cancelReservation(id);
                return ResponseEntity.ok(new ResponseSuccess<>(
                                OK,
                                "Hủy đặt bàn thành công",
                                null));
        }
}
