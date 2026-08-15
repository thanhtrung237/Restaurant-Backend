package com.group.restaurant.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "promotion_branch", uniqueConstraints = {
        @UniqueConstraint(name = "uk_promotion_branch", columnNames = {"branch_id", "promotion_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionBranch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_promotion_branch_branch"))
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_promotion_branch_promotion"))
    private Promotion promotion;
}
