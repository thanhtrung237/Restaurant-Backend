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
public class TableSessionMergeRequest {

    @NotNull(message = "Phiên nguồn cần gộp không được để trống")
    private Long sourceSessionId;

    @NotNull(message = "Phiên đích (bàn chính) không được để trống")
    private Long targetSessionId;
}
