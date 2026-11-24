package com.group.restaurant.models;

import com.group.restaurant.enums.TableSessionStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "table_session")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableSession extends BaseEntity {
    @Column(name = "guest_access_version", length = 36)
    private String guestAccessVersion;

    @Column(name = "guest_join_code", length = 8)
    private String guestJoinCode;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", foreignKey = @ForeignKey(name = "fk_session_table"))
    private DiningTable table;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    public boolean isTakeaway() {
        return table == null;
    }

    public String getDisplayName() {
        return isTakeaway() ? "Mang về #" + id : table.getName();
    }

    public boolean canAddTakeawayItems(boolean hasInvoice) {
        return isTakeaway()
                && (status == TableSessionStatus.SERVING
                        || (status == TableSessionStatus.WAITING_FOR_PAY && !hasInvoice));
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "branch_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_session_branch"))
    private Branch branch;

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

    // BR-08: snapshot VAT và phí dịch vụ tại thời điểm mở phiên
    @Column(name = "vat_rate_at_open", precision = 5, scale = 2)
    private BigDecimal vatRateAtOpen;

    @Column(name = "service_fee_rate_at_open", precision = 5, scale = 2)
    private BigDecimal serviceFeeRateAtOpen;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private TableSessionStatus status = TableSessionStatus.WAITING_FOR_OPEN;

    // === BỔ SUNG CÁC TRƯỜNG BỊ THIẾU DƯỚI ĐÂY ===
    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "reservation_name", length = 200)
    private String reservationName;

    @Column(name = "reservation_phone", length = 20)
    private String reservationPhone;

    @Column(name = "reservation_time")
    private LocalDateTime reservationTime;

    @Column(name = "reservation_note", length = 500)
    private String reservationNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "merged_into_session_id",
            foreignKey = @ForeignKey(name = "fk_session_merged_into"))
    private TableSession mergedIntoSession;
}
