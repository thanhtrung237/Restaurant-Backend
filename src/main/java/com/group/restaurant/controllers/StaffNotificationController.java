package com.group.restaurant.controllers;

import com.group.restaurant.configurations.jwt.AccountUserDetails;
import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.services.StaffNotificationInbox;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.HttpStatus.OK;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/staff-notifications/branch/{branchId}")
@PreAuthorize("hasAnyRole('MANAGER', 'BRANCH_MANAGER', 'WAITER', 'CASHIER')")
public class StaffNotificationController {
    private final StaffNotificationInbox inbox;

    @GetMapping
    public ResponseSuccess<List<StaffNotificationInbox.Entry>> list(
            @PathVariable Long branchId, @AuthenticationPrincipal AccountUserDetails staff) {
        return new ResponseSuccess<>(OK, "Danh sách thông báo", inbox.list(branchId, staff.getAccountId()));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long branchId, @PathVariable Long id,
                                         @AuthenticationPrincipal AccountUserDetails staff) {
        inbox.markOneRead(branchId, staff.getAccountId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read")
    public ResponseEntity<Void> markAllRead(@PathVariable Long branchId, @AuthenticationPrincipal AccountUserDetails staff) {
        inbox.updateAll(branchId, staff.getAccountId(), false);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> hideAll(@PathVariable Long branchId, @AuthenticationPrincipal AccountUserDetails staff) {
        inbox.updateAll(branchId, staff.getAccountId(), true);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<Void> resolve(@PathVariable Long branchId, @PathVariable Long id,
                                        @AuthenticationPrincipal AccountUserDetails staff) {
        inbox.resolve(branchId, staff.getAccountId(), id);
        return ResponseEntity.noContent().build();
    }
}
