package com.group.restaurant.dtos.request.branch;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/** Cấu hình giờ giấc và timeout. Các trường tài khoản ngân hàng yêu cầu quyền Quản trị nhà hàng. */
@Getter
@Setter
public class BranchManagerConfigRequest {

    private LocalTime openingTime;

    private LocalTime closingTime;

    private LocalTime businessDayStartTime;

    @Size(max = 50, message = "Số tài khoản không được vượt quá 50 ký tự")
    private String bankAccountNo;

    @Size(max = 200, message = "Tên ngân hàng không được vượt quá 200 ký tự")
    private String bankName;

    // ------------------------------------------------------------------------------
    // PHÂN BIỆT "KHÔNG GỬI FIELD" VỚI "GỬI NULL ĐỂ XOÁ TRẮNG".
    //
    // Trước đây service chỉ có `if (request.getX() != null)` nên không cách nào gỡ được
    // cấu hình đã đặt: gửi null bị bỏ qua, mà `businessDayStartTime` kiểu LocalTime thì
    // không gửi chuỗi rỗng được.
    //
    // Jackson chỉ gọi setter khi field CÓ MẶT trong JSON, kể cả khi giá trị là null.
    // Viết tay 3 setter để đánh dấu "đã gửi" — không cần thư viện mới (Optional /
    // JsonNullable). Lombok @Setter bỏ qua field đã có setter viết tay.
    // ------------------------------------------------------------------------------

    @JsonIgnore private boolean businessDayStartTimeProvided;
    @JsonIgnore private boolean bankAccountNoProvided;
    @JsonIgnore private boolean bankNameProvided;

    public void setBusinessDayStartTime(LocalTime businessDayStartTime) {
        this.businessDayStartTime = businessDayStartTime;
        this.businessDayStartTimeProvided = true;
    }

    public void setBankAccountNo(String bankAccountNo) {
        this.bankAccountNo = bankAccountNo;
        this.bankAccountNoProvided = true;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
        this.bankNameProvided = true;
    }

    @Min(value = 1, message = "Timeout CHỜ MỞ BÀN phải >= 1 phút")
    private Integer pendingConfirmTimeoutMinutes;

    @Min(value = 1, message = "Timeout ĐẶT TRƯỚC phải >= 1 phút")
    private Integer reservationHoldMinutes;
}
