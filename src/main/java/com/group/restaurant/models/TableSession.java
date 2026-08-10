package com.group.restaurant.models;

import com.group.restaurant.enums.TableSessionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "table_session")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false, foreignKey = @ForeignKey(name = "fk_session_table"))
    private DiningTable table;

    @Column(name = "guest_count")
    private Integer guestCount;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "cover_fee_per_guest_at_open", precision = 12, scale = 2)
    private BigDecimal coverFeePerGuestAtOpen;

    @Column(name = "customer_id_ref", length = 100)
    private String customerIdRef;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private TableSessionStatus status = TableSessionStatus.OPEN;
}
