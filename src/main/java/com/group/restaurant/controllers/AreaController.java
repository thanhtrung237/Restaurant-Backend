package com.group.restaurant.controllers;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import com.group.restaurant.dtos.request.area.AreaAddRequest;
import com.group.restaurant.dtos.request.area.AreaUpdateRequest;
import com.group.restaurant.dtos.response.area.AreaResponse;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.enums.AreaStatus;
import com.group.restaurant.services.AreaService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/areas")
@RequiredArgsConstructor
public class AreaController {

    private final AreaService areaService;

    @PostMapping("")
    public ResponseEntity<ResponseSuccess<AreaResponse>> createArea(
            @Valid @RequestBody AreaAddRequest request) {
        return ResponseEntity.status(CREATED)
                .body(
                        new ResponseSuccess<>(
                                CREATED,
                                "Tạo khu vực thành công",
                                areaService.createArea(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseSuccess<AreaResponse>> updateArea(
            @PathVariable Long id, @Valid @RequestBody AreaUpdateRequest request) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK, "Cập nhật khu vực thành công", areaService.updateArea(id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseSuccess<AreaResponse>> getAreaById(@PathVariable Long id) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK, "Lấy thông tin khu vực thành công", areaService.getAreaById(id)));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ResponseSuccess<List<AreaResponse>>> getAreasByBranch(
            @PathVariable Long branchId, @RequestParam(required = false) AreaStatus status) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy danh sách khu vực theo chi nhánh thành công",
                        areaService.getAreasByBranch(branchId, status)));
    }

    @GetMapping("")
    public ResponseEntity<ResponseSuccess<List<AreaResponse>>> getAreas(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) AreaStatus status) {
        if (branchId != null) {
            return ResponseEntity.ok(
                    new ResponseSuccess<>(
                            OK,
                            "Lấy danh sách khu vực theo chi nhánh thành công",
                            areaService.getAreasByBranch(branchId, status)));
        }
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK, "Lấy danh sách tất cả khu vực thành công", areaService.getAllAreas()));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ResponseSuccess<Void>> changeStatus(
            @PathVariable Long id, @RequestParam AreaStatus status) {
        areaService.changeStatus(id, status);
        return ResponseEntity.ok(
                new ResponseSuccess<>(OK, "Thay đổi trạng thái khu vực thành công", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseSuccess<Void>> deleteArea(@PathVariable Long id) {
        areaService.deleteArea(id);
        return ResponseEntity.ok(new ResponseSuccess<>(OK, "Xóa khu vực thành công", null));
    }
}
