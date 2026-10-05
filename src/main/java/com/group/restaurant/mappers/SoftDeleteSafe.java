package com.group.restaurant.mappers;

import jakarta.persistence.EntityNotFoundException;

import java.util.function.Function;

/**
 * Đọc một trường của thực thể được THAM CHIẾU mà không để bản ghi đã xoá mềm làm sập cả API.
 *
 * Mọi @Entity trong dự án đều có {@code @SQLRestriction("is_deleted = false")}. Khi bản ghi
 * đích bị xoá mềm SAU lúc bản ghi nguồn đã trỏ tới nó, proxy lazy không giải được và lần đầu
 * chạm vào bất kỳ trường nào (trừ id) sẽ ném {@link EntityNotFoundException}. Nếu mapper chạm
 * thẳng thì **cả trang** trả 500, không riêng dòng hỏng — đúng những gì đã xảy ra với
 * {@code /audit-logs} (be-issues mục 19) và {@code /invoices/branch/{id}} (mục 19.1).
 *
 * Quy ước: mất tên của một thực thể đã xoá thì trả {@code null} cho ĐÚNG trường đó, còn dữ
 * liệu còn lại vẫn đọc được. Dữ liệu lịch sử (hoá đơn, nhật ký) phải đọc được kể cả khi danh
 * mục / bàn / chi nhánh gốc đã bị xoá.
 *
 * Hướng lâu dài là lưu tên dạng GIÁ TRỊ ngay trên bản ghi lịch sử lúc ghi, thay vì khoá ngoại —
 * cần đổi schema nên chưa làm (be-issues mục 19).
 */
public final class SoftDeleteSafe {

    private SoftDeleteSafe() {
    }

    /**
     * @param ref    thực thể được tham chiếu (có thể null, có thể là proxy của bản ghi đã xoá mềm)
     * @param getter cách lấy giá trị cần đọc
     * @return giá trị đọc được, hoặc {@code null} nếu {@code ref} null / bản ghi đã bị xoá mềm
     */
    public static <T, R> R get(T ref, Function<T, R> getter) {
        if (ref == null) {
            return null;
        }
        try {
            return getter.apply(ref);
        } catch (EntityNotFoundException e) {
            return null;
        }
    }
}
