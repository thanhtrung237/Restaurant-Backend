package com.group.restaurant.dtos.request.optionItem;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class OptionItemAddRequest {

    @NotNull(message = "Option group ID không được để trống")
    private Long optionGroupId;

    @NotBlank(message = "Tên lựa chọn không được để trống")
    @Size(max = 200, message = "Tên không được vượt quá 200 ký tự")
    private String name;

    @DecimalMin(value = "0", message = "Giá cộng thêm phải >= 0")
    private BigDecimal extraPrice = BigDecimal.ZERO;
}
