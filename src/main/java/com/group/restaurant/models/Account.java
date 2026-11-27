package com.group.restaurant.models;

import com.group.restaurant.enums.AccountStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
        name = "account",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_account_restaurant_username",
                    columnNames = {"restaurant_id", "username"}),
            @UniqueConstraint(name = "uk_account_restaurant_phone",
                    columnNames = {"restaurant_id", "phone"})
        })
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "restaurant_id",
            nullable = true) // nullable=true để hỗ trợ Platform Admin (không thuộc nhà hàng nào)
    private Restaurant restaurant;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "employee_code", nullable = false, length = 20)
    private String employeeCode;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(length = 100)
    private String email;

    @Column(length = 10)
    private String phone;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @OneToMany(
            mappedBy = "account",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<Assignment> assignments = new ArrayList<>();
}
