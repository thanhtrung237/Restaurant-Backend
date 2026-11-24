package com.group.restaurant.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
        name = "dish_option_group",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_dish_option_group",
                    columnNames = {"dish_id", "option_group_id"})
        })
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishOptionGroup extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "dish_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_dish_option_group_dish"))
    private Dish dish;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "option_group_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_dish_option_group_group"))
    private OptionGroup optionGroup;
}
