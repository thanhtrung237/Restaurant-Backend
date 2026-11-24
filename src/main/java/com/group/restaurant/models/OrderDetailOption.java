package com.group.restaurant.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "order_detail_option")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailOption extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_detail_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_detail_option_detail"))
    private OrderDetail orderDetail;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "option_item_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_detail_option_item"))
    private OptionItem optionItem;

    @Column(name = "price_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceSnapshot;
}
