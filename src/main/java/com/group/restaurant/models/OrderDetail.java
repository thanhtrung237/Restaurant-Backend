package com.group.restaurant.models;

import com.group.restaurant.enums.CancelReason;
import com.group.restaurant.enums.OrderDetailStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_detail")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetail extends BaseEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_detail_order"))
        private Order order;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "table_session_id", foreignKey = @ForeignKey(name = "fk_order_detail_session"))
        private TableSession tableSession;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "dish_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_detail_dish"))
        private Dish dish;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "promotion_id", foreignKey = @ForeignKey(name = "fk_order_detail_promotion"))
        private Promotion promotion;

        @Column(name = "quantity", nullable = false)
        private Integer quantity;

        @Column(name = "served_quantity")
        private Integer servedQuantity;

        public int getServedQuantity() {
                if (status == OrderDetailStatus.SERVED) return quantity;
                return servedQuantity == null ? 0 : servedQuantity;
        }

        @Column(name = "price_at_order_time", nullable = false, precision = 12, scale = 2)
        private BigDecimal priceAtOrderTime;

        @Column(name = "promotion_discount_amount", precision = 12, scale = 2)
        private BigDecimal promotionDiscountAmount;

        @Column(name = "weight_kg", precision = 8, scale = 3)
        private BigDecimal weightKg;

        @Column(name = "is_take_away", nullable = false)
        @Builder.Default
        private Boolean isTakeAway = false;

        @Column(name = "note", columnDefinition = "TEXT")
        private String note;

        @Column(name = "status", nullable = false)
        @Builder.Default
        private OrderDetailStatus status = OrderDetailStatus.PENDING;

        @Column(name = "price", precision = 12, scale = 2)
        private BigDecimal price;

        @Column(name = "is_printed", nullable = false)
        @Builder.Default
        private boolean isPrinted = false;

        @Column(name = "print_count", nullable = false)
        @Builder.Default
        private int printCount = 0;

        @Column(name = "cancel_reason")
        private CancelReason cancelReason;

        @Column(name = "cancelled_at")
        private LocalDateTime cancelledAt;

        @Column(name = "cancelled_by")
        private Long cancelledBy;

        // true = dòng món đã hủy nhưng vẫn tính tiền (lý do: khách đổi ý)
        @Column(name = "is_billable", nullable = false)
        @Builder.Default
        private Boolean isBillable = true;
}
