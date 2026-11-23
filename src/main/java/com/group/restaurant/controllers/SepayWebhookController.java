package com.group.restaurant.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.group.restaurant.services.SepayTransfer;
import com.group.restaurant.services.SepayWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("${api.prefix}/webhooks/sepay")
@RequiredArgsConstructor
public class SepayWebhookController {
    private final ObjectMapper json;
    private final SepayTransfer sepay;
    private final SepayWebhookService transactions;

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Transfer(Long id, String gateway, String accountNumber, String content,
                            String transferType, BigDecimal transferAmount, BigDecimal accumulated) {}

    @PostMapping(consumes = APPLICATION_JSON_VALUE)
    public Map<String, Boolean> receive(HttpServletRequest request,
                                       @RequestHeader(value = "X-SePay-Timestamp", required = false) String timestamp,
                                       @RequestHeader(value = "X-SePay-Signature", required = false) String signature)
            throws IOException {
        byte[] body = request.getInputStream().readNBytes(16_385);
        if (body.length > 16_384) throw new ResponseStatusException(PAYLOAD_TOO_LARGE);
        if (!sepay.isAuthentic(timestamp, signature, body)) throw new ResponseStatusException(UNAUTHORIZED);

        Transfer transfer;
        try {
            transfer = json.readValue(body, Transfer.class);
        } catch (IOException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Dữ liệu webhook không hợp lệ.");
        }
        if (transfer == null || transfer.id() == null || transfer.id() <= 0 || transfer.transferAmount() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Thiếu mã giao dịch hoặc số tiền.");
        }
        try {
            transactions.record(transfer.id(), transfer.gateway(), transfer.accountNumber(), transfer.transferType(),
                    transfer.content(), transfer.transferAmount(), transfer.accumulated());
        } catch (DataIntegrityViolationException | PersistenceException e) {
            // A concurrent delivery may have committed this same SePay transaction.
            if (!transactions.hasNotice(transfer.id())) throw e;
        }
        return Map.of("success", true);
    }
}
