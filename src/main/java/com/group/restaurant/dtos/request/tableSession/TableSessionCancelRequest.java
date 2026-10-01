package com.group.restaurant.dtos.request.tableSession;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Hủy phiên bàn (ĐANG PHỤC VỤ → ĐÃ HỦY).
 *
 * Bảng 2.2 của đặc tả: hủy phiên do Quản lý chi nhánh thực hiện, <b>bắt buộc nhập lý do</b>,
 * và phải ghi nhật ký. Lý do lưu vào cột `note` sẵn có của phiên — không thêm cột mới.
 */
@Getter
@Setter
public class TableSessionCancelRequest {

    @NotBlank(message = "Phải nhập lý do huỷ phiên")
    @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự")
    private String reason;
}
