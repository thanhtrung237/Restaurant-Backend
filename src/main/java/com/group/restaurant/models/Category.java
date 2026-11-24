package com.group.restaurant.models;

import com.group.restaurant.enums.CategoryStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "category")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_category_restaurant"))
    private Restaurant restaurant;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "status", nullable = false)
    private CategoryStatus status;
}
