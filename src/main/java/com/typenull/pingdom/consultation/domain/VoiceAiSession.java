package com.typenull.pingdom.consultation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI provider와 분리된 인증 사용자 세션.
 * 대화 원문·provider 응답은 엔티티 저장 범위에서 제외. 응답 재전송용 envelope와 원문 해시는 VoiceAiReplay에 별도 저장.
 */
@Getter
@Entity
@Table(name = "voice_ai_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoiceAiSession {

    @Id
    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoiceAiSessionStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    private VoiceAiSession(String sessionId, Long userId, LocalDateTime expiresAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.status = VoiceAiSessionStatus.ACTIVE;
        this.expiresAt = expiresAt;
    }

    public static VoiceAiSession create(String sessionId, Long userId, LocalDateTime expiresAt) {
        return new VoiceAiSession(sessionId, userId, expiresAt);
    }

    public boolean belongsTo(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isUsableAt(LocalDateTime now) {
        return status == VoiceAiSessionStatus.ACTIVE && expiresAt.isAfter(now);
    }

    public void refresh(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    /** 종료 요청의 반복 호출에도 CLOSED 상태를 유지해 DELETE 멱등성 보장. */
    public void close(LocalDateTime now) {
        if (status == VoiceAiSessionStatus.ACTIVE) {
            status = VoiceAiSessionStatus.CLOSED;
            closedAt = now;
        }
    }
}
