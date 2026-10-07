package com.group.restaurant.dtos.response.base;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

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
