package com.group.restaurant.services.impl;

import com.group.restaurant.dtos.response.invoice.BillPreviewResponse;
import com.group.restaurant.enums.InvoiceStatus;
import com.group.restaurant.exceptions.ResourceNotFoundException;
import com.group.restaurant.models.Branch;
import com.group.restaurant.models.Invoice;
import com.group.restaurant.models.ManualDiscount;
import com.group.restaurant.models.OrderDetail;
import com.group.restaurant.models.TableSession;
import com.group.restaurant.repositories.InvoiceRepository;
import com.group.restaurant.repositories.OrderDetailRepository;
import com.group.restaurant.repositories.TableSessionRepository;
import com.group.restaurant.services.BillService;
import com.group.restaurant.services.billing.BillBreakdown;
import com.group.restaurant.services.billing.BillCalculator;
import com.group.restaurant.services.billing.BillCalculator.PromotionSelection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillServiceImpl implements BillService {

    private final TableSessionRepository tableSessionRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillCalculator billCalculator;

    @Override
    public BillPreviewResponse previewByTableSession(Long tableSessionId) {
        TableSession session =
                tableSessionRepository
                        .findById(tableSessionId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Không tìm thấy phiên làm việc với ID: "
                                                        + tableSessionId));

        List<OrderDetail> details = orderDetailRepository.findByTableSessionId(session.getId());

        // Đã có hóa đơn (chưa hủy) → dùng đúng số đã chốt trên hóa đơn.
        Invoice invoice =
                invoiceRepository
                        .findByTableSessionId(session.getId())
                        .filter(i -> i.getStatus() != InvoiceStatus.CANCELLED)
                        .orElse(null);

        BillBreakdown bill;
        if (invoice != null) {
            bill = billCalculator.describeInvoice(invoice, details);
        } else {
            Branch branch = session.getBranch();
            List<ManualDiscount> manualDiscounts =
                    billCalculator.loadManualDiscounts(session.getId(), null);
            bill =
                    billCalculator.calculate(
                            session, branch, details, manualDiscounts, PromotionSelection.auto());
        }

        return BillPreviewResponse.builder()
                .tableSessionId(session.getId())
                .sessionStatus(session.getStatus())
                .invoiceId(invoice != null ? invoice.getId() : null)
                .invoiceStatus(invoice != null ? invoice.getStatus() : null)
                .grossSubTotal(bill.getGrossSubTotal())
                .itemPromotionDiscount(bill.getItemPromotionDiscount())
                .orderPromotionDiscount(bill.getOrderPromotionDiscount())
                .orderPromotionId(bill.getOrderPromotionId())
                .orderPromotionName(bill.getOrderPromotionName())
                .manualDiscount(bill.getManualDiscount())
                .amountAfterDiscount(bill.getAmountAfterDiscount())
                .serviceFeeRate(bill.getServiceFeeRate())
                .serviceFee(bill.getServiceFee())
                .vatRate(bill.getVatRate())
                .vatOnServiceFee(bill.isVatOnServiceFee())
                .vatBase(bill.getVatBase())
                .vatAmount(bill.getVatAmount())
                .grandTotal(bill.getGrandTotal())
                .hasPendingWeight(bill.isHasPendingWeight())
                .build();
    }
}
