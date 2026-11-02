package com.group.restaurant.models;

import com.group.restaurant.enums.RoleScope;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "role", uniqueConstraints = {
        @UniqueConstraint(name = "uk_role_restaurant_code", columnNames = {"restaurant_id", "code"})
})
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

   
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = true) // nullable=true để hỗ trợ Platform role (scope=PLATFORM)
    private Restaurant restaurant;
    
    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private RoleScope scope;

    @Column(nullable = false, length = 50)
    private String code;

    @OneToMany(mappedBy = "role", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @Builder.Default
    private List<Assignment> assignments = new ArrayList<>();
}
