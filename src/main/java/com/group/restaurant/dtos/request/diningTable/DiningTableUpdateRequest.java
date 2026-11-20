package com.group.restaurant.dtos.request.diningTable;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DiningTableUpdateRequest {

    private Long areaId;
    private boolean clearArea;

    @Size(max = 50, message = "Tên bàn không được vượt quá 50 ký tự")
    private String name;

    @Min(value = 1, message = "Số chỗ ngồi phải >= 1")
    private Integer capacity;

    @Size(max = 200, message = "Mã QR không được vượt quá 200 ký tự")
    private String qrCode;
}
