package com.group.restaurant.services;

import com.group.restaurant.dtos.request.manualDiscount.ManualDiscountAddRequest;
import com.group.restaurant.dtos.response.invoice.InvoiceResponse;
import com.group.restaurant.dtos.response.manualDiscount.ManualDiscountResponse;
import com.group.restaurant.models.InvoiceDiscountApproval;

import java.util.List;

public interface ManualDiscountService {

    ManualDiscountResponse createManualDiscount(ManualDiscountAddRequest request);

    ManualDiscountResponse getManualDiscountById(Long id);

    List<ManualDiscountResponse> getByOrder(Long orderId);

    List<ManualDiscountResponse> getByInvoice(Long invoiceId);

    void deleteManualDiscount(Long id);

    InvoiceResponse replaceInvoiceDiscount(Long invoiceId, ManualDiscountAddRequest request);

    InvoiceResponse clearInvoiceDiscount(Long invoiceId);

    InvoiceDiscountApproval getApproval(Long invoiceId);

    InvoiceResponse decideApproval(Long invoiceId, String requestId, boolean approved);
}
