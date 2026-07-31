

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class TableSessionOpenRequest {

    @NotNull(message = "Table ID không được để trống")
    private Long tableId;

    @Min(value = 1, message = "Số khách phải >= 1")
    private Integer guestCount;

    private String note;

    private BigDecimal coverFeePerGuestAtOpen;

    private String customerIdRef;
}
