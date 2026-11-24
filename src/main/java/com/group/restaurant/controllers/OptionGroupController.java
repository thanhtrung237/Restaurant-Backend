package com.group.restaurant.controllers;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import com.group.restaurant.dtos.request.optionGroup.OptionGroupAddRequest;
import com.group.restaurant.dtos.request.optionGroup.OptionGroupUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.optionGroup.OptionGroupResponse;
import com.group.restaurant.services.OptionGroupService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/option-groups")
@RequiredArgsConstructor
public class OptionGroupController {

    private final OptionGroupService optionGroupService;

    @PostMapping("")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<OptionGroupResponse>> createOptionGroup(
            @Valid @RequestBody OptionGroupAddRequest request) {
        return ResponseEntity.status(CREATED)
                .body(
                        new ResponseSuccess<>(
                                CREATED,
                                "Tạo nhóm tùy chọn thành công",
                                optionGroupService.createOptionGroup(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<OptionGroupResponse>> updateOptionGroup(
            @PathVariable Long id, @Valid @RequestBody OptionGroupUpdateRequest request) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Cập nhật nhóm tùy chọn thành công",
                        optionGroupService.updateOptionGroup(id, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseSuccess<OptionGroupResponse>> getOptionGroupById(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy nhóm tùy chọn thành công",
                        optionGroupService.getOptionGroupById(id)));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ResponseSuccess<List<OptionGroupResponse>>> getOptionGroupsByRestaurant(
            @PathVariable Long restaurantId) {
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy danh sách nhóm tùy chọn theo nhà hàng thành công",
                        optionGroupService.getOptionGroupsByRestaurant(restaurantId)));
    }

    @GetMapping("")
    public ResponseEntity<ResponseSuccess<PageResponse<OptionGroupResponse>>> getAllOptionGroups(
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                new ResponseSuccess<>(
                        OK,
                        "Lấy danh sách nhóm tùy chọn thành công",
                        optionGroupService.getAllOptionGroups(restaurantId, keyword, pageable)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<Void>> deleteOptionGroup(@PathVariable Long id) {
        optionGroupService.deleteOptionGroup(id);
        return ResponseEntity.ok(new ResponseSuccess<>(OK, "Xóa nhóm tùy chọn thành công", null));
    }
}
