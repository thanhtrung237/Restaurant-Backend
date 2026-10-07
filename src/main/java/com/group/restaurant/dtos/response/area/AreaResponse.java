package com.group.restaurant.dtos.response.area;

import com.group.restaurant.enums.AreaStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class AreaResponse {

    private Long id;
    private Long branchId;
    private String branchName;
    private String name;
    private AreaStatus status;
    private Integer tableCount;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
