package com.group.restaurant.dtos.response.diningTable;

import com.group.restaurant.enums.DiningTableStatus;
import com.group.restaurant.enums.TableSessionStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class QrScanResponse {

    // ── Thông tin bàn ──────────────────────────────────────────────
    private Long tableId;
    private String tableName;
    private Integer tableCapacity;

    // ── Thông tin chi nhánh ────────────────────────────────────────
    private Long branchId;
    private String branchName;
    private String branchAddress;

    // ── Thông tin phiên bàn (null nếu chưa có phiên) ───────────────
    private Long sessionId;
    private TableSessionStatus sessionStatus;

    // ── Trạng thái bàn ─────────────────────────────────────────────
    private DiningTableStatus tableStatus;

    /**
     * Hành động FE cần thực hiện sau khi quét QR:
     *   WAITING_FOR_STAFF — Phiên mới tạo, chờ nhân viên xác nhận mở bàn
     *   JOIN_EXISTING     — Bàn đang SERVING, tham gia phiên hiện tại
     *   RESERVED          — Bàn đã đặt trước, không tự quét được
     *   BRANCH_INACTIVE   — Chi nhánh ngưng hoạt động
     */
    private ScanAction scanAction;

    /** Thông báo hiển thị cho khách */
    private String message;

    public enum ScanAction {
        WAITING_FOR_STAFF,
        JOIN_EXISTING,
        RESERVED,
        BRANCH_INACTIVE
    }
}
