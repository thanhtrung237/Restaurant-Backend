package com.group.restaurant.models;

import com.group.restaurant.enums.DiscountType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "manual_discount")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualDiscount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "approved_by",
            foreignKey = @ForeignKey(name = "fk_manual_discount_approved_by"))
    private Account approvedBy;

    // Người thực hiện chiết khấu (thu ngân bấm) — BR-10, FR-PRO-02
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "applied_by",
            foreignKey = @ForeignKey(name = "fk_manual_discount_applied_by"))
    private Account appliedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", foreignKey = @ForeignKey(name = "fk_manual_discount_order"))
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", foreignKey = @ForeignKey(name = "fk_manual_discount_invoice"))
    private Invoice invoice;

    // FR-PRO-02: chiết khấu áp cho từng dòng món (null = áp cho toàn hóa đơn)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "order_detail_id",
            foreignKey = @ForeignKey(name = "fk_manual_discount_order_detail"))
    private OrderDetail orderDetail;

    @Column(name = "type", nullable = false)
    private DiscountType type;

    @Column(name = "value", nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;
}
