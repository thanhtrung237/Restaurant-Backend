package com.group.restaurant.mappers;

import com.group.restaurant.dtos.request.invoice.InvoiceCreateRequest;
import com.group.restaurant.dtos.response.invoice.InvoiceListResponse;
import com.group.restaurant.dtos.response.invoice.InvoiceResponse;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.Invoice;
import com.group.restaurant.models.Promotion;
import com.group.restaurant.models.TableSession;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Map hoá đơn sang response.
 *
 * Các trường lấy qua quan hệ (`branch`, `tableSession`, `tableSession.table`, `promotion`) đều đi
 * qua {@link SoftDeleteSafe}: bản ghi đích bị XOÁ MỀM sau khi hoá đơn đã trỏ tới nó thì proxy
 * không giải được và chạm vào tên là {@code EntityNotFoundException} — trước đây làm
 * {@code GET /invoices/branch/{id}} trả **500 cho cả danh sách** chỉ vì một dòng
 * (be-issues mục 19.1: hoá đơn id=5 trỏ tới bàn `[TEST]` đã xoá).
 *
 * Hoá đơn là dữ liệu lịch sử: phải đọc được kể cả khi bàn hay chi nhánh gốc đã bị xoá.
 */
@Mapper(componentModel = "spring", uses = {PaymentMapper.class}, unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        imports = {SoftDeleteSafe.class, Branch.class, TableSession.class, Promotion.class})
public interface InvoiceMapper {

    @Mapping(target = "branch.id", source = "branchId")
    @Mapping(target = "tableSession.id", source = "tableSessionId")
    @Mapping(target = "promotion.id", source = "promotionId")
    Invoice toEntity(InvoiceCreateRequest request);

    @Mapping(target = "branchId", expression = "java(SoftDeleteSafe.get(invoice.getBranch(), Branch::getId))")
    @Mapping(target = "branchName", expression = "java(SoftDeleteSafe.get(invoice.getBranch(), Branch::getName))")
    @Mapping(target = "tableSessionId", expression = "java(SoftDeleteSafe.get(invoice.getTableSession(), TableSession::getId))")
    @Mapping(target = "takeaway", expression = "java(SoftDeleteSafe.get(invoice.getTableSession(), TableSession::isTakeaway))")
    @Mapping(target = "tableName", expression = "java(InvoiceMapper.tableNameOf(invoice))")
    @Mapping(target = "promotionId", expression = "java(SoftDeleteSafe.get(invoice.getPromotion(), Promotion::getId))")
    @Mapping(target = "promotionName", expression = "java(SoftDeleteSafe.get(invoice.getPromotion(), Promotion::getName))")
    @Mapping(target = "payments", source = "payments")
    @Mapping(target = "totalPaid", ignore = true)
    @Mapping(target = "remainingAmount", ignore = true)
    // MapStruct tự map eInvoiceRef, eInvoiceIssuedAt, taxCodeUsed,
    //            eInvoiceProvider, eInvoiceAttemptCount vì tên field khớp
    InvoiceResponse toResponse(Invoice invoice);

    @Mapping(target = "branchName", expression = "java(SoftDeleteSafe.get(invoice.getBranch(), Branch::getName))")
    @Mapping(target = "tableName", expression = "java(InvoiceMapper.tableNameOf(invoice))")
    InvoiceListResponse toListResponse(Invoice invoice);

    /**
     * Tên bàn của hoá đơn — đi qua HAI quan hệ (`tableSession` → `table`), nên cả hai đều có thể
     * là proxy của bản ghi đã xoá mềm. Một lớp bọc là đủ vì lambda chạy bên trong try/catch.
     */
    static String tableNameOf(Invoice invoice) {
        return SoftDeleteSafe.get(invoice.getTableSession(),
                session -> session.isTakeaway() ? session.getDisplayName()
                        : SoftDeleteSafe.get(session.getTable(), table -> table.getName()));
    }
}
