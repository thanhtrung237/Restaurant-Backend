package com.group.restaurant.repositories;

import com.group.restaurant.models.SepayDiscordNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface SepayDiscordNoticeRepository extends JpaRepository<SepayDiscordNotice, Long> {
    List<SepayDiscordNotice> findTop25ByAttemptedAtIsNullOrderByTransactionIdAsc();

    @Transactional
    @Modifying
    @Query("update SepayDiscordNotice n set n.attemptedAt = :now where n.transactionId = :id and n.attemptedAt is null")
    int claim(long id, Instant now);

    @Transactional
    @Modifying
    @Query("update SepayDiscordNotice n set n.sentAt = :now where n.transactionId = :id")
    void markSent(long id, Instant now);
}
