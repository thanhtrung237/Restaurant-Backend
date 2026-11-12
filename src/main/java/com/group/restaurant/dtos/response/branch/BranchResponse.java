package com.group.restaurant.dtos.response.branch;

import com.group.restaurant.enums.BranchStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;


@Getter
@Setter
@Builder
public class BranchResponse {
    private Double latitude;
    private Double longitude;
    private boolean geofenceEnabled;
    private int geofenceRadiusMeters;
    private int geofenceMaxAccuracyMeters;


    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private String code;
    private String name;
    private String address;
    private String phone;
    private LocalTime openingTime;
    private LocalTime closingTime;
    private LocalTime businessDayStartTime;
    private String taxCode;
    private String bankAccountNo;
    private String bankName;
    private BranchStatus status;
    private BigDecimal serviceFeeRate;
    private BigDecimal vatRate;
    private Boolean vatAppliedOnServiceFee;
    private Integer discountApprovalThreshold;
    private Integer pendingConfirmTimeoutMinutes;
    private Integer reservationHoldMinutes;
    private Integer tableCount;
    private Integer activeSessionCount;
    //cảnh báo khi đổi VAT/phí dịch vụ mà còn phiên đang mở
    private Boolean hasOpenSessionWarning;
    private Integer openSessionCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
