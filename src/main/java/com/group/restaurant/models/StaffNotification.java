package com.group.restaurant.models;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "staff_notification",
        indexes = @Index(name = "idx_staff_notification_branch", columnList = "branch_id,id"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "table_session_id", nullable = false)
    private Long tableSessionId;

    @Column(name = "table_name", nullable = false)
    private String tableName;

    @Column(nullable = false, length = 24)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private Type type;

    private Long orderId;
    private Long orderDetailId;
    private String dishName;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant resolvedAt;
    private Long resolvedById;

    public void resolve(Long accountId, Instant now) {
        if (resolvedAt == null) {
            resolvedAt = now;
            resolvedById = accountId;
        }
    }

    public enum Type {
        TABLE_OPEN_REQUESTED,
        NEW_ORDER,
        PAYMENT_REQUESTED,
        STAFF_CALL,
        PAYMENT_QR_OPENED,
        BANK_PAYMENT_VERIFIED,
        DISCOUNT_APPROVAL
    }
}
