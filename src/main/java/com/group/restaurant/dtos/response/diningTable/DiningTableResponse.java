package com.group.restaurant.dtos.response.diningTable;

import com.group.restaurant.enums.DiningTableStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class DiningTableResponse {

    private Long id;
    private Long branchId;
    private String branchName;
    private Long areaId;
    private String areaName;
    private String name;
    private Integer capacity;
    private String qrCode;
    private DiningTableStatus status;
    private Boolean hasOpenSession;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
