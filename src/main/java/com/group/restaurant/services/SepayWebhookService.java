package com.group.restaurant.services;

import com.group.restaurant.models.SepayDiscordNotice;
import com.group.restaurant.repositories.SepayDiscordNoticeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SepayWebhookService {
    private final SepayTransfer sepay;
    private final PaymentService payments;
    private final SepayDiscordNoticeRepository notices;
    private final DiscordTransactionSender discord;
    private final EntityManager entityManager;

    @Transactional
    public void record(long transactionId, String gateway, String accountNumber, String type,
                       String content, BigDecimal amount, BigDecimal balance) {
        boolean incoming = "in".equals(type);
        if (!(incoming || "out".equals(type)) || amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 0 || !sepay.matchesDestination(gateway, accountNumber)) return;

        boolean notify = discord.enabled();
        if (notify && hasNotice(transactionId)) return;

        Long invoiceId = incoming ? sepay.invoiceId(content) : null;
        boolean paid = invoiceId != null && payments.recordVerifiedSepayTransfer(invoiceId, transactionId, amount);
        if (notify) {
            String outcome = paid ? sepay.invoiceCode(invoiceId) + " · Đã thanh toán"
                    : incoming ? "Chờ đối chiếu" : "Tiền ra";
            String message = "MB " + (incoming ? "+" : "−") + vnd(amount)
                    + " · " + outcome + " · SePay #" + transactionId;
            if (balance != null && balance.signum() != 0) {
                message += "\nSố dư sau giao dịch · " + vnd(balance);
            }
            entityManager.persist(new SepayDiscordNotice(transactionId, message));
            entityManager.flush();
        }
    }

    public boolean hasNotice(long transactionId) {
        return notices.existsById(transactionId);
    }

    private String vnd(BigDecimal amount) {
        return NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(amount) + "đ";
    }
}
