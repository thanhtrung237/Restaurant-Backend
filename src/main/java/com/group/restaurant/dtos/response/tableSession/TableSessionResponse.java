package com.group.restaurant.dtos.response.tableSession;

import com.group.restaurant.enums.TableSessionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TableSessionResponse {

    private Long id;
    private Long tableId;
    private String tableName;
    private boolean takeaway;
    private boolean canAddTakeawayItems;
    private LocalDateTime pickedUpAt;
    private Long branchId;
    private Long restaurantId;
    private Integer guestCount;
    private String note;
    private BigDecimal coverFeePerGuestAtOpen;
    private String customerIdRef;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private TableSessionStatus status;
    private Integer orderCount;

    // snapshot lúc mở phiên
    private BigDecimal vatRateAtOpen;
    private BigDecimal serviceFeeRateAtOpen;

    private String reservationName;
    private String reservationPhone;
    private LocalDateTime reservationTime;
    private String reservationNote;

    /**
     * Người MỞ phiên bàn — lấy từ cột audit `create_by` có sẵn trên BaseEntity
     * (JpaAuditingConfig điền username, hoặc "guest" khi khách tự quét QR).
     * Là USERNAME, không phải họ tên đầy đủ — xem docs/todo.md.
     */
    private String openedBy;

    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}
