package com.group.restaurant.models;

import com.group.restaurant.enums.OrderDetailStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "order_detail")
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

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "menu_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_detail_menu"))
        private Menu menu;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "promotion_id", foreignKey = @ForeignKey(name = "fk_order_detail_promotion"))
        private Promotion promotion;

        @Column(name = "quantity", nullable = false)
        private Integer quantity;

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
}
