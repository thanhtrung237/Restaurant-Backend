package com.group.restaurant.models;

import com.group.restaurant.enums.AreaStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "area", uniqueConstraints = {
                @UniqueConstraint(name = "uk_area_branch_name", columnNames = { "branch_id", "name" })
})
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Area extends BaseEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "branch_id", nullable = false, foreignKey = @ForeignKey(name = "fk_area_branch"))
        private Branch branch;

        @Column(name = "name", nullable = false, length = 100)
        private String name;

        @Column(name = "status", nullable = false)
        @Builder.Default
        private AreaStatus status = AreaStatus.ACTIVE;
}
