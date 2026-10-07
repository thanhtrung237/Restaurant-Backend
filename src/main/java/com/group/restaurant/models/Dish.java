package com.group.restaurant.models;

import com.group.restaurant.enums.DishStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import java.math.BigDecimal;

@Entity
@Table(name = "dish")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dish extends BaseEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "restaurant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_dish_restaurant"))
        private Restaurant restaurant;

        // null = món gốc cấp nhà hàng; có giá trị = món riêng của chi nhánh đó (FR-MNU-04, BR-15)
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "branch_id", foreignKey = @ForeignKey(name = "fk_dish_branch"))
        private Branch branch;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "category_id", nullable = false, foreignKey = @ForeignKey(name = "fk_dish_category"))
        private Category category;


        @Column(name = "name", nullable = false, length = 200)
        private String name;

        @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
        private BigDecimal basePrice;

        @Column(name = "tax_group", length = 50)
        private String taxGroup;

        @Column(name = "kitchen_station_type", length = 50)
        private String kitchenStationType;

        @Column(name = "image_url", length = 500)
        private String imageUrl;

        @Column(name = "sold_by_weight", nullable = false)
        private Boolean soldByWeight;

        @Column(name = "status", nullable = false)
        private DishStatus status;

        @Column(name = "description", columnDefinition = "TEXT")
        private String description;
}
