package com.group.restaurant.models;

import com.group.restaurant.enums.DiscountType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "manual_discount")
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
        @JoinColumn(name = "approved_by", foreignKey = @ForeignKey(name = "fk_manual_discount_approved_by"))
        private Account approvedBy;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "order_id", foreignKey = @ForeignKey(name = "fk_manual_discount_order"))
        private Order order;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "invoice_id", foreignKey = @ForeignKey(name = "fk_manual_discount_invoice"))
        private Invoice invoice;

        @Column(name = "type", nullable = false)
        private DiscountType type;

        @Column(name = "value", nullable = false, precision = 10, scale = 2)
        private BigDecimal value;

        @Column(name = "reason", nullable = false, length = 500)
        private String reason;
}
