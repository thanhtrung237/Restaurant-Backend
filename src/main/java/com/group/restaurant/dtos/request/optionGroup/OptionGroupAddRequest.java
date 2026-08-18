package com.group.restaurant.dtos.request.optionGroup;

import com.group.restaurant.enums.SelectionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OptionGroupAddRequest {

    @NotNull(message = "Restaurant ID không được để trống")
    private Long restaurantId;

    @NotBlank(message = "Tên nhóm tùy chọn không được để trống")
    @Size(max = 200, message = "Tên không được vượt quá 200 ký tự")
    private String name;

    @NotNull(message = "Kiểu chọn không được để trống")
    private SelectionType selectionType;

    @NotNull(message = "Số lượng chọn tối thiểu không được để trống")
    @Min(value = 0, message = "Số lượng chọn tối thiểu phải >= 0")
    private Integer minSelect;

    @NotNull(message = "Số lượng chọn tối đa không được để trống")
    @Min(value = 1, message = "Số lượng chọn tối đa phải >= 1")
    private Integer maxSelect;
}
