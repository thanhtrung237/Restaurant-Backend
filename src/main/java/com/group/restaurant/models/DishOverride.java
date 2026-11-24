package com.group.restaurant.models;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
        name = "dish_override",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_dish_override_branch_dish",
                    columnNames = {"branch_id", "dish_id"})
        })
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishOverride extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "branch_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_dish_override_branch"))
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "dish_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_dish_override_dish"))
    private Dish dish;

    @Column(name = "override_price", precision = 10, scale = 2)
    private BigDecimal overridePrice;

    @Column(name = "is_hidden", nullable = false)
    private Boolean isHidden;
}
