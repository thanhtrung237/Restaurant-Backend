package com.group.restaurant.models;

import com.group.restaurant.enums.StockStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
        name = "branch_dish_stock",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_branch_dish_stock",
                    columnNames = {"branch_id", "dish_id"})
        })
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchDishStock extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "branch_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_branch_dish_stock_branch"))
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "dish_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_branch_dish_stock_dish"))
    private Dish dish;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private StockStatus status = StockStatus.AVAILABLE;
}
