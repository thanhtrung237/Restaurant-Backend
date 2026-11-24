package com.group.restaurant.services;

import com.group.restaurant.models.Branch;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SepayTransfer {
    private static final Pattern INVOICE_CODE = Pattern.compile("(?i)\\bPV([1-9][0-9]{0,17})X\\b");
    private final String secret;
    private final String accountNumber;
    private final String bankCode;

    public SepayTransfer(
            @Value("${SEPAY_WEBHOOK_SECRET:}") String secret,
            @Value("${SEPAY_ACCOUNT_NUMBER:${SEPAY_SACOMBANK_ACCOUNT:}}") String accountNumber,
            @Value("${SEPAY_BANK:STB}") String bank) {
        this.secret = secret;
        this.accountNumber = accountNumber.trim();
        this.bankCode = normalizeBank(bank);
    }

    public boolean enabledFor(Branch branch) {
        return !secret.isBlank()
                && !accountNumber.isBlank()
                && branch != null
                && matchesDestination(branch.getBankName(), branch.getBankAccountNo());
    }

    public boolean matchesDestination(String gateway, String account) {
        return !bankCode.isEmpty()
                && bankCode.equals(normalizeBank(gateway))
                && sameAccount(account);
    }

    private boolean sameAccount(String account) {
        return account != null && accountNumber.equals(account.replaceAll("\\s+", ""));
    }

    private static String normalizeBank(String name) {
        if (name == null) return "";
        return switch (name.toUpperCase(Locale.ROOT).replaceAll("\\s+", "")) {
            case "MB", "MBBANK" -> "MB";
            case "STB", "SACOMBANK" -> "STB";
            default -> "";
        };
    }

    public String invoiceCode(Long invoiceId) {
        return "PV" + invoiceId + "X";
    }

    public Long invoiceId(String content) {
        if (content == null) return null;
        var matcher = INVOICE_CODE.matcher(content);
        if (!matcher.find()) return null;
        long id = Long.parseLong(matcher.group(1));
        return matcher.find() ? null : id;
    }

    public boolean isAuthentic(String timestamp, String signature, byte[] body) {
        if (secret.isBlank()
                || accountNumber.isBlank()
                || timestamp == null
                || signature == null
                || !timestamp.matches("[0-9]{1,12}")
                || !signature.matches("sha256=[0-9a-fA-F]{64}")) return false;
        long now = Instant.now().getEpochSecond();
        long signedAt = Long.parseLong(timestamp);
        if (signedAt < now - 300 || signedAt > now + 300) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.US_ASCII));
            return MessageDigest.isEqual(
                    mac.doFinal(body), HexFormat.of().parseHex(signature.substring(7)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
        }
    }
}
