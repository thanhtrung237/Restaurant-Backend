package com.group.restaurant.controllers;

import com.group.restaurant.dtos.request.optionItem.OptionItemAddRequest;
import com.group.restaurant.dtos.request.optionItem.OptionItemUpdateRequest;
import com.group.restaurant.dtos.response.base.PageResponse;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.optionItem.OptionItemResponse;
import com.group.restaurant.services.OptionItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/option-items")
@RequiredArgsConstructor
public class OptionItemController {

    private final OptionItemService optionItemService;

    @PostMapping("")
    public ResponseEntity<ResponseSuccess<OptionItemResponse>> createOptionItem(
            @Valid @RequestBody OptionItemAddRequest request) {
        return ResponseEntity.status(CREATED).body(new ResponseSuccess<>(
                CREATED,
                "Tạo lựa chọn thành công",
                optionItemService.createOptionItem(request)
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseSuccess<OptionItemResponse>> updateOptionItem(
            @PathVariable Long id,
            @Valid @RequestBody OptionItemUpdateRequest request) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Cập nhật lựa chọn thành công",
                optionItemService.updateOptionItem(id, request)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseSuccess<OptionItemResponse>> getOptionItemById(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy lựa chọn thành công",
                optionItemService.getOptionItemById(id)
        ));
    }

    @GetMapping("/group/{optionGroupId}")
    public ResponseEntity<ResponseSuccess<List<OptionItemResponse>>> getOptionItemsByGroup(
            @PathVariable Long optionGroupId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách lựa chọn theo nhóm thành công",
                optionItemService.getOptionItemsByGroup(optionGroupId)
        ));
    }

    @GetMapping("")
    public ResponseEntity<ResponseSuccess<PageResponse<OptionItemResponse>>> getAllOptionItems(
            @RequestParam(required = false) Long optionGroupId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy danh sách lựa chọn thành công",
                optionItemService.getAllOptionItems(optionGroupId, keyword, pageable)
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseSuccess<Void>> deleteOptionItem(@PathVariable Long id) {
        optionItemService.deleteOptionItem(id);
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Xóa lựa chọn thành công",
                null
        ));
    }
}
