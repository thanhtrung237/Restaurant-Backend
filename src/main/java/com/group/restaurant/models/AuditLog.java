package com.group.restaurant.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private Account performedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Account approvedBy;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    /**
     * ID của bản ghi bị tác động (invoice nào, order-detail nào...).
     * Thiếu cột này thì nhật ký chỉ nói "có ai đó hủy một hóa đơn" mà không truy được
     * hóa đơn nào — không dùng được để đối soát.
     */
    @Column(name = "entity_id")
    private Long entityId;

    // FR-LOG-01: loại hành động để lọc nhật ký (STATUS_CHANGE, CREATE, UPDATE, DELETE...)
    @Column(name = "action_type", length = 50)
    private String actionType;

    @Column(name = "value_before", columnDefinition = "TEXT")
    private String valueBefore;

    @Column(name = "value_after", columnDefinition = "TEXT")
    private String valueAfter;

    @Column(length = 500)
    private String reason;
}
