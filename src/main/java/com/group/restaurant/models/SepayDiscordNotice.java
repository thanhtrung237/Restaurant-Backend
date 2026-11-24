package com.group.restaurant.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sepay_discord_notice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SepayDiscordNotice {
    @Id private Long transactionId;

    @Column(nullable = false, length = 500)
    private String message;

    private Instant attemptedAt;
    private Instant sentAt;

    public SepayDiscordNotice(long transactionId, String message) {
        this.transactionId = transactionId;
        this.message = message;
    }
}
