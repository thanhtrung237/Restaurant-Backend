package com.group.restaurant.models;

import jakarta.persistence.*;

@Entity
@Table(name = "staff_notification_receipt", uniqueConstraints = @UniqueConstraint(
        name = "uk_staff_notification_receipt", columnNames = {"account_id", "notification_id"}))
public class StaffNotificationReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;
}
