package com.group.restaurant.dtos.request.orderDetail;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailBatchConfirmRequest {

    @NotEmpty(message = "Danh sách ID món cần xác nhận không được để trống")
    private List<Long> orderDetailIds;
}
