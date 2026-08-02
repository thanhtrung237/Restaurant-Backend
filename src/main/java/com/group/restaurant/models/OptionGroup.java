package com.group.restaurant.models;

import com.group.restaurant.enums.SelectionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;


@Entity
@Table(name = "option_group")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionGroup extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_option_group_restaurant"))
    private Restaurant restaurant;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "selection_type", nullable = false)
    private SelectionType selectionType;

    @Column(name = "min_select", nullable = false)
    private int minSelect;

    @Column(name = "max_select", nullable = false)
    private int maxSelect;
}
