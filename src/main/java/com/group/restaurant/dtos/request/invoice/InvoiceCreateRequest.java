package com.group.restaurant.dtos.request.invoice;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvoiceCreateRequest {

    @NotNull(message = "Branch ID không được để trống")
    private Long branchId;

    @NotNull(message = "Table Session ID không được để trống")
    private Long tableSessionId;

    private Long promotionId;

    @Size(max = 500, message = "Ghi chú hóa đơn không được vượt quá 500 ký tự")
    private String invoiceNote;
}
