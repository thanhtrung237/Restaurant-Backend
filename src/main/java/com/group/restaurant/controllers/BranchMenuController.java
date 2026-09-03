package com.group.restaurant.controllers;

import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.dtos.response.branch.BranchMenuItemResponse;
import com.group.restaurant.services.BranchMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}/branches")
@RequiredArgsConstructor
public class BranchMenuController {

    private final BranchMenuService branchMenuService;


    @GetMapping("/{branchId}/menu")
    public ResponseEntity<ResponseSuccess<List<BranchMenuItemResponse>>> getMenuForCustomer(
            @PathVariable Long branchId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy menu chi nhánh thành công",
                branchMenuService.getMenuForCustomer(branchId)
        ));
    }


    @GetMapping("/{branchId}/menu/overview")
    public ResponseEntity<ResponseSuccess<List<BranchMenuItemResponse>>> getMenuOverview(
            @PathVariable Long branchId) {
        return ResponseEntity.ok(new ResponseSuccess<>(
                OK,
                "Lấy tổng quan menu chi nhánh thành công",
                branchMenuService.getMenuOverviewForAdmin(branchId)
        ));
    }
}
