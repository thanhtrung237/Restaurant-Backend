package com.group.restaurant.dtos.response.base;

import java.util.Date;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ResponseError {
    private Date timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
}
