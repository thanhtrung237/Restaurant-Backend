package com.group.restaurant.controllers;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

import com.group.restaurant.dtos.response.base.ResponseSuccess;
import com.group.restaurant.services.TakeawayService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/takeaway-orders/branch/{branchId}")
@PreAuthorize("hasAnyRole('WAITER', 'CASHIER', 'BRANCH_MANAGER', 'MANAGER')")
public class TakeawayController {
    private final TakeawayService takeaways;

    @GetMapping
    public ResponseSuccess<List<TakeawayService.Entry>> list(@PathVariable Long branchId) {
        return new ResponseSuccess<>(OK, "Danh sách đơn mang về", takeaways.list(branchId));
    }

    @PostMapping
    public ResponseEntity<ResponseSuccess<TakeawayService.Entry>> create(
            @PathVariable Long branchId, @Valid @RequestBody TakeawayService.Create request) {
        return ResponseEntity.status(CREATED)
                .body(
                        new ResponseSuccess<>(
                                CREATED,
                                "Đã gửi đơn mang về",
                                takeaways.create(branchId, request)));
    }

    @PutMapping("/{id}/handoff")
    public ResponseEntity<Void> handoff(@PathVariable Long branchId, @PathVariable Long id) {
        takeaways.handoff(branchId, id);
        return ResponseEntity.noContent().build();
    }
}
