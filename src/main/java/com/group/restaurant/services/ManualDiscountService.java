package com.group.restaurant.services;

import com.group.restaurant.dtos.request.manualDiscount.ManualDiscountAddRequest;
import com.group.restaurant.dtos.response.manualDiscount.ManualDiscountResponse;

import java.util.List;

public interface ManualDiscountService {

    ManualDiscountResponse createManualDiscount(ManualDiscountAddRequest request);

    ManualDiscountResponse getManualDiscountById(Long id);

    List<ManualDiscountResponse> getByOrder(Long orderId);

    List<ManualDiscountResponse> getByInvoice(Long invoiceId);

    void deleteManualDiscount(Long id);
}
