package com.group.restaurant.repositories;

import com.group.restaurant.models.StaffNotification;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StaffNotificationRepository extends JpaRepository<StaffNotification, Long> {
    interface InboxEntry {
        StaffNotification getNotification();
        boolean getIsRead();
    }

    @Query("""
            select n as notification, coalesce(r.read, false) as isRead
            from StaffNotification n left join StaffNotificationReceipt r
              on r.notificationId = n.id and r.accountId = :accountId
            where n.branchId = :branchId and coalesce(r.hidden, false) = false
            order by n.id desc
            """)
    List<InboxEntry> findInbox(Long branchId, Long accountId, Pageable limit);

    Optional<StaffNotification> findByIdAndBranchId(Long id, Long branchId);

    boolean existsByTableSessionIdAndType(Long tableSessionId, StaffNotification.Type type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from StaffNotification n where n.id = :id and n.branchId = :branchId")
    Optional<StaffNotification> findForResolution(Long branchId, Long id);

    @Modifying
    @Query(value = """
            insert into staff_notification_receipt (account_id, notification_id, is_read, is_hidden)
            select :accountId, n.id, true, :hide from staff_notification n
            where n.branch_id = :branchId and (:id = 0 or n.id = :id)
              and not exists (select 1 from staff_notification_receipt r
                  where r.notification_id = n.id and r.account_id = :accountId)
            """, nativeQuery = true)
    void insertReceipts(Long branchId, Long accountId, long id, boolean hide);

    @Modifying
    @Query(value = """
            update staff_notification_receipt set is_read = true, is_hidden = (is_hidden or :hide)
            where account_id = :accountId and notification_id in
              (select id from staff_notification where branch_id = :branchId and (:id = 0 or id = :id))
            """, nativeQuery = true)
    void updateReceipts(Long branchId, Long accountId, long id, boolean hide);

}
