package com.group.restaurant.models;

import com.group.restaurant.enums.DiningTableStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "dining_table", uniqueConstraints = {
                @UniqueConstraint(name = "uk_table_branch_qr", columnNames = { "branch_id", "qr_code" }),
                @UniqueConstraint(name = "uk_table_branch_name", columnNames = { "branch_id", "name" }) // BR-16
})
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiningTable extends BaseEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "branch_id", nullable = false, foreignKey = @ForeignKey(name = "fk_table_branch"))
        private Branch branch;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "area_id", foreignKey = @ForeignKey(name = "fk_table_area"))
        private Area area;

        @Column(name = "name", nullable = false, length = 50)
        private String name;

        @Column(name = "capacity")
        private Integer capacity;

        @Column(name = "qr_code", length = 200)
        private String qrCode;

        @Column(name = "status", nullable = false)
        @Builder.Default
        private DiningTableStatus status = DiningTableStatus.AVAILABLE;
}
