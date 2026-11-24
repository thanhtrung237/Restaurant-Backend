package com.group.restaurant.dtos.request.orderDetail;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailBatchConfirmRequest {

    @NotEmpty(message = "Danh sách ID món cần xác nhận không được để trống")
    private List<Long> orderDetailIds;
}
