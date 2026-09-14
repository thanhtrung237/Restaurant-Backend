package com.group.restaurant.dtos.response.branch;

import com.group.restaurant.enums.BranchStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Builder
public class BranchListResponse {

    private Long id;
    private String code;
    private String name;
    private String address;
    private String phone;
    private BranchStatus status;
    private Integer tableCount;
}
