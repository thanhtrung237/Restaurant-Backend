package com.group.restaurant.dtos.request.branch;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** CHỈ Quản trị nhà hàng sửa được: VAT, phí dịch vụ, ngưỡng chiết khấu, mã số thuế riêng */
@Getter
@Setter
public class BranchAdminConfigRequest {

    @DecimalMin(value = "0.00", message = "Thuế VAT phải >= 0")
    @DecimalMax(value = "100.00", message = "Thuế VAT phải <= 100")
    private BigDecimal vatRate;

    @DecimalMin(value = "0.00", message = "Phí dịch vụ phải >= 0")
    @DecimalMax(value = "100.00", message = "Phí dịch vụ phải <= 100")
    private BigDecimal serviceFeeRate;

    // Áp VAT lên cả phí dịch vụ
    private Boolean vatAppliedOnServiceFee;

    @Min(value = 0, message = "Ngưỡng chiết khấu phải >= 0")
    private Integer discountApprovalThreshold;

    @Size(max = 50, message = "Mã số thuế không được vượt quá 50 ký tự")
    private String taxCode;
}
