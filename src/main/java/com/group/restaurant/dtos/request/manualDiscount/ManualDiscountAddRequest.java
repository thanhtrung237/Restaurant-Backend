package com.group.restaurant.dtos.request.manualDiscount;

import com.group.restaurant.enums.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManualDiscountAddRequest {

    private Long orderId;

    private Long invoiceId;

    @NotNull(message = "Loại giảm giá không được để trống")
    private DiscountType type;

    @NotNull(message = "Giá trị giảm không được để trống")
    private BigDecimal value;

    @NotBlank(message = "Lý do không được để trống")
    @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự")
    private String reason;
}
