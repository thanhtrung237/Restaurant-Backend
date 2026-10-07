package com.group.restaurant.dtos.request.optionGroup;

import com.group.restaurant.enums.SelectionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OptionGroupUpdateRequest {

    @Size(max = 200, message = "Tên không được vượt quá 200 ký tự")
    private String name;

    private SelectionType selectionType;

    @Min(value = 0, message = "Số lượng chọn tối thiểu phải >= 0")
    private Integer minSelect;

    @Min(value = 1, message = "Số lượng chọn tối đa phải >= 1")
    private Integer maxSelect;
}
