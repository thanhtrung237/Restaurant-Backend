package com.group.restaurant.dtos.request.tableSession;

import jakarta.validation.constraints.NotNull;
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
public class TableSessionTransferRequest {

    @NotNull(message = "Bàn đích không được để trống")
    private Long targetTableId;
}
