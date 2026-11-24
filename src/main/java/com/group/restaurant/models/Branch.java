package com.group.restaurant.models;

import com.group.restaurant.enums.BranchStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
        name = "branch",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_branch_restaurant_code",
                    columnNames = {"restaurant_id", "code"})
        })
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Branch extends BaseEntity {

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "geofence_enabled", nullable = false)
    private boolean geofenceEnabled = false;

    @Column(name = "geofence_radius_meters", nullable = false)
    private int geofenceRadiusMeters = 150;

    @Column(name = "geofence_max_accuracy_meters", nullable = false)
    private int geofenceMaxAccuracyMeters = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_branch_restaurant"))
    private Restaurant restaurant;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "opening_time")
    private LocalTime openingTime;

    @Column(name = "closing_time")
    private LocalTime closingTime;

    @Column(name = "business_day_start_time")
    private LocalTime businessDayStartTime;

    @Column(name = "tax_code", length = 50)
    private String taxCode;

    @Column(name = "bank_account_no", length = 50)
    private String bankAccountNo;

    @Column(name = "status", nullable = false)
    private BranchStatus status = BranchStatus.ACTIVE;

    @Column(name = "service_fee_rate", precision = 5, scale = 2)
    private BigDecimal serviceFeeRate;

    @Column(name = "vat_rate", precision = 5, scale = 2)
    private BigDecimal vatRate;

    // Áp VAT lên cả phí dịch vụ
    @Column(name = "vat_applied_on_service_fee", nullable = false)
    private Boolean vatAppliedOnServiceFee = false;

    @Column(name = "discount_approval_threshold")
    private Integer discountApprovalThreshold;

    @Column(name = "bank_name", length = 200)
    private String bankName;

    @Column(name = "pending_confirm_timeout_minutes")
    private Integer pendingConfirmTimeoutMinutes = 10;

    @Column(name = "reservation_hold_minutes")
    private Integer reservationHoldMinutes = 15;
}
