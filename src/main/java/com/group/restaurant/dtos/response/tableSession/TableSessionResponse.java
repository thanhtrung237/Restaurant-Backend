package com.group.restaurant.dtos.response.tableSession;

import com.group.restaurant.enums.TableSessionStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class TableSessionResponse {

    private Long id;
    private Long tableId;
    private String tableName;
    private Integer guestCount;
    private String note;
    private BigDecimal coverFeePerGuestAtOpen;
    private String customerIdRef;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private TableSessionStatus status;
    private Integer orderCount;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
