package com.group.restaurant.dtos.request.branch;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// FR-BRN-01 Tạo chi nhánh thứ hai trở đi: cho phép sao chép cấu hình từ chi nhánh có sẵn.

@Getter
@Setter
public class BranchCopyConfigRequest {

    @NotNull(message = "Source Branch ID không được để trống")
    private Long sourceBranchId;

    private boolean copyOpeningHours = true;
    private boolean copyFeeRates = true;
    private boolean copyBankInfo = true;
}
