package com.group.restaurant.controllers;

import static org.springframework.http.HttpStatus.OK;

import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.services.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("${api.prefix}/upload")
@RequiredArgsConstructor
public class UploadController {

    private final CloudinaryService cloudinaryService;

    // Quản trị nhà hàng và QL chi nhánh upload ảnh món ăn
    @PostMapping("/dish")
    @PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<ResponseSuccess<String>> uploadDishImage(
            @RequestParam("file") MultipartFile file) {
        String url = cloudinaryService.uploadImage(file, "dishes");
        return ResponseEntity.ok(new ResponseSuccess<>(OK, "Upload ảnh thành công", url));
    }

    // Quản trị nhà hàng upload logo
    @PostMapping("/restaurant")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseSuccess<String>> uploadRestaurantLogo(
            @RequestParam("file") MultipartFile file) {
        String url = cloudinaryService.uploadImage(file, "restaurants");
        return ResponseEntity.ok(new ResponseSuccess<>(OK, "Upload logo thành công", url));
    }
}
