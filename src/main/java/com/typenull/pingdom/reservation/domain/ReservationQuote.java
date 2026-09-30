package com.typenull.pingdom.reservation.domain;

import com.typenull.pingdom.reservation.domain.exception.ReservationErrorCode;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 사용자 소유의 불투명 확인 토큰과 최종 거절 결과. 예약자 개인정보 원문은 보관하지 않음. */
@Entity
@Table(name = "reservation_quote", uniqueConstraints = @UniqueConstraint(name = "uq_reservation_quote_user_key",
        columnNames = {"tourist_user_id", "idempotency_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationQuote {
    @Id @Column(length = 36) private String id;
    @Column(name = "tourist_user_id", nullable = false) private Long touristUserId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb") private ReservationConfirmation confirmation;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "idempotency_key", length = 100) private String idempotencyKey;
    @Column(name = "request_fingerprint", length = 64) private String requestFingerprint;
    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_code", length = 64) private ReservationErrorCode rejectionCode;

    public static ReservationQuote issue(Long userId, ReservationConfirmation confirmation, Instant now) {
        ReservationQuote quote = new ReservationQuote();
        quote.id = UUID.randomUUID().toString();
        quote.touristUserId = userId;
        quote.confirmation = confirmation;
        quote.createdAt = now;
        return quote;
    }

    public void bind(String key, String fingerprint) {
        idempotencyKey = key;
        requestFingerprint = fingerprint;
    }

    public void reject(ReservationErrorCode code) { rejectionCode = code; }
}
