package com.group.restaurant.services.einvoice;

import com.group.restaurant.models.Invoice;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StubInvoiceProvider implements InvoiceProvider {

    private static final String PROVIDER_NAME = "stub";

    @Override
    public EInvoiceResult issue(Invoice invoice) throws EInvoiceException {
        log.warn(
                "[STUB] Phát hành HĐĐT giả lập cho hóa đơn #{} ({}). "
                        + "Thay thế StubInvoiceProvider bằng nhà cung cấp thật trước khi lên production.",
                invoice.getId(),
                invoice.getInvoiceNumber());

        // Sinh ref giả: STUB-<invoiceNumber>-<uuid ngắn>
        String fakeRef =
                "STUB-"
                        + invoice.getInvoiceNumber()
                        + "-"
                        + UUID.randomUUID()
                                .toString()
                                .replace("-", "")
                                .substring(0, 8)
                                .toUpperCase();

        return EInvoiceResult.builder()
                .ref(fakeRef)
                .issuedAt(LocalDateTime.now())
                .providerName(PROVIDER_NAME)
                .build();
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }
}
