package com.group.restaurant.controllers;

import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.services.guest.GuestAdmissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("${api.prefix}")
@RequiredArgsConstructor
public class GuestAccessController {
    private final GuestAdmissionService admission;

    @GetMapping("/guest/table")
    public ResponseEntity<ResponseSuccess<GuestAdmissionService.TableInfo>> table(@RequestParam("branchId") Long branchId, @RequestParam("qrCode") String qrCode) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                new ResponseSuccess<>(OK, "Thông tin bàn", admission.info(branchId, qrCode)));
    }
    @PostMapping("/guest/access")
    public ResponseEntity<ResponseSuccess<GuestAdmissionService.AccessResponse>> enter(
            @Valid @RequestBody GuestAdmissionService.AccessRequest body, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                new ResponseSuccess<>(OK, "Tiếp nhận yêu cầu", admission.enter(body, request.getRemoteAddr())));
    }
    @PostMapping("/table-sessions/{id}/guest-code")
    @PreAuthorize("hasAnyRole('WAITER','CASHIER','BRANCH_MANAGER','MANAGER')")
    public ResponseEntity<ResponseSuccess<String>> code(@PathVariable("id") Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                new ResponseSuccess<>(OK, "Chỉ cung cấp mã cho khách tại bàn", admission.staffCode(id)));
    }
}
