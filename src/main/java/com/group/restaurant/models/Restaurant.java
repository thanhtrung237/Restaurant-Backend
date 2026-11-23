package com.group.restaurant.models;

import com.group.restaurant.enums.RestaurantStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "restaurant", uniqueConstraints = {
        @UniqueConstraint(name = "uk_restaurant_code", columnNames = "code")
})
@SQLRestriction("is_deleted = false")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Restaurant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "tax_code", length = 50)
    private String taxCode;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "status", nullable = false)
    private RestaurantStatus status = RestaurantStatus.ACTIVE;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "operational_permissions", columnDefinition = "TEXT")
    private String operationalPermissions;

}
