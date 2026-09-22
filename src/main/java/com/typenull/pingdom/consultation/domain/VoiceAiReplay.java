package com.typenull.pingdom.consultation.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 세션 수명 안에서 동일 요청의 provider 결과를 안정적으로 재전송하기 위한 최소 ledger.
 * PROCESSING은 provider 호출의 소유권만 기록하므로 외부 호출 동안 DB 트랜잭션을 유지하지 않는다.
 */
@Getter
@Entity
@Table(name = "voice_ai_replay")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoiceAiReplay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voice_ai_replay_id")
    private Long id;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Column(name = "request_id", nullable = false, length = 128)
    private String requestId;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode envelope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoiceAiReplayStatus status;

    @Column(name = "processing_token", length = 36)
    private String processingToken;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private VoiceAiReplay(String sessionId, String requestId, String payloadHash, String processingToken,
                          LocalDateTime processingStartedAt, LocalDateTime createdAt) {
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.payloadHash = payloadHash;
        this.status = VoiceAiReplayStatus.PROCESSING;
        this.processingToken = processingToken;
        this.processingStartedAt = processingStartedAt;
        this.createdAt = createdAt;
    }

    public static VoiceAiReplay start(String sessionId, String requestId, String payloadHash,
                                      String processingToken, LocalDateTime now) {
        return new VoiceAiReplay(sessionId, requestId, payloadHash, processingToken, now, now);
    }

    public boolean hasSamePayload(String payloadHash) {
        return this.payloadHash.equals(payloadHash);
    }

    public boolean isCompleted() {
        return status == VoiceAiReplayStatus.COMPLETED;
    }

    public boolean isProcessingLeaseExpiredAt(LocalDateTime now, java.time.Duration processingLease) {
        return status == VoiceAiReplayStatus.PROCESSING
                && (processingStartedAt == null || !processingStartedAt.plus(processingLease).isAfter(now));
    }

    /** 만료된 소유권만 인계한다. 늦게 끝난 이전 provider 응답은 token 비교에서 폐기된다. */
    public void takeOverProcessing(String processingToken, LocalDateTime now) {
        this.processingToken = processingToken;
        this.processingStartedAt = now;
    }

    public boolean complete(String processingToken, JsonNode envelope) {
        if (status != VoiceAiReplayStatus.PROCESSING || !Objects.equals(this.processingToken, processingToken)) {
            return false;
        }
        this.envelope = envelope;
        this.status = VoiceAiReplayStatus.COMPLETED;
        this.processingToken = null;
        this.processingStartedAt = null;
        return true;
    }

    public boolean isClaimedBy(String processingToken) {
        return status == VoiceAiReplayStatus.PROCESSING && Objects.equals(this.processingToken, processingToken);
    }
}
