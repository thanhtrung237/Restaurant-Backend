package com.group.restaurant.services;

import com.group.restaurant.dtos.request.invoice.InvoiceCreateRequest;
import com.group.restaurant.dtos.response.invoice.InvoiceListResponse;
import com.group.restaurant.dtos.response.invoice.InvoiceResponse;
import com.group.restaurant.enums.InvoiceStatus;

import java.util.List;

public interface InvoiceService {

    InvoiceResponse createInvoice(InvoiceCreateRequest request);

    InvoiceResponse getInvoiceById(Long id);

    InvoiceResponse getInvoiceByTableSession(Long tableSessionId);

    List<InvoiceListResponse> getInvoicesByBranch(Long branchId, InvoiceStatus status);

    void cancelInvoice(Long id);

    void deleteInvoice(Long id);
}
