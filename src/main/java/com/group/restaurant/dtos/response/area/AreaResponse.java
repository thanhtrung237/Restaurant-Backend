package com.group.restaurant.dtos.response.area;

import com.group.restaurant.enums.AreaStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

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
