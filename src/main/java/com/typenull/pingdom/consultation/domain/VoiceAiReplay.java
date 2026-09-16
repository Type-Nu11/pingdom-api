package com.typenull.pingdom.consultation.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 세션 수명 안에서 동일 요청의 provider 결과를 안정적으로 재전송하기 위한 최소 ledger. */
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
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode envelope;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private VoiceAiReplay(String sessionId, String requestId, String payloadHash, JsonNode envelope, LocalDateTime createdAt) {
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.payloadHash = payloadHash;
        this.envelope = envelope;
        this.createdAt = createdAt;
    }

    public static VoiceAiReplay create(String sessionId, String requestId, String payloadHash,
                                       JsonNode envelope, LocalDateTime createdAt) {
        return new VoiceAiReplay(sessionId, requestId, payloadHash, envelope, createdAt);
    }

    public boolean hasSamePayload(String payloadHash) {
        return this.payloadHash.equals(payloadHash);
    }
}
