package com.group.restaurant.dtos.request.tableSession;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TableSessionUpdateRequest {

    @Min(value = 1, message = "Số khách phải >= 1")
    private Integer guestCount;

    private String note;

    private String customerIdRef;
}
