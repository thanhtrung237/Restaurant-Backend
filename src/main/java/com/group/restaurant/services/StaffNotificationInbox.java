package com.group.restaurant.services;

import com.group.restaurant.exceptions.ConflictException;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.models.StaffNotification;
import com.group.restaurant.repositories.AccountRepository;
import com.group.restaurant.repositories.StaffNotificationRepository;
import com.group.restaurant.utils.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffNotificationInbox {
    private final StaffNotificationRepository notifications;
    private final AccountRepository accounts;
    private final TenantGuard tenantGuard;

    public record Entry(Long id, Long branchId, Long tableSessionId, String tableName,
                        StaffNotification.Type type, Long orderId, Long orderDetailId, String dishName,
                        Instant createdAt, Instant resolvedAt, boolean read) {}

    @Transactional(readOnly = true)
    public List<Entry> list(Long branchId, Long accountId) {
        tenantGuard.assertBranchAssigned(branchId);
        return notifications.findInbox(branchId, accountId, PageRequest.of(0, 50)).stream().map(row -> {
            StaffNotification n = row.getNotification();
            return new Entry(n.getId(), n.getBranchId(), n.getTableSessionId(), n.getTableName(),
                    n.getType(), n.getOrderId(), n.getOrderDetailId(), n.getDishName(),
                    n.getCreatedAt(), n.getResolvedAt(), row.getIsRead());
        }).toList();
    }

    @Transactional
    public void markOneRead(Long branchId, Long accountId, Long id) {
        lockAccount(accountId);
        tenantGuard.assertBranchAssigned(branchId);
        find(branchId, id);
        writeReceipts(branchId, accountId, id, false);
    }

    @Transactional
    public void updateAll(Long branchId, Long accountId, boolean hide) {
        lockAccount(accountId);
        tenantGuard.assertBranchAssigned(branchId);
        writeReceipts(branchId, accountId, 0, hide);
    }

    @Transactional
    public void resolve(Long branchId, Long accountId, Long id) {
        lockAccount(accountId);
        tenantGuard.assertBranchAssigned(branchId);
        StaffNotification notification = notifications.findForResolution(branchId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo."));
        if (notification.getType() != StaffNotification.Type.STAFF_CALL) {
            throw new ConflictException("Chỉ yêu cầu gọi nhân viên mới có thể đánh dấu đã xử lý.");
        }
        notification.resolve(accountId, Instant.now());
        writeReceipts(branchId, accountId, id, false);
    }

    private void lockAccount(Long accountId) {
        accounts.findForNotificationUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản."));
    }

    private StaffNotification find(Long branchId, Long id) {
        return notifications.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo."));
    }

    private void writeReceipts(Long branchId, Long accountId, long id, boolean hide) {
        notifications.insertReceipts(branchId, accountId, id, hide);
        notifications.updateReceipts(branchId, accountId, id, hide);
    }
}
