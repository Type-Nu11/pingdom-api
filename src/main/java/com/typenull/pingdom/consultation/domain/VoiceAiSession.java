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
 * AI provider와 분리된 인증 사용자 세션입니다.
 * 대화 원문과 provider 응답은 개인정보 보호를 위해 저장하지 않습니다.
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

    /** 종료 요청은 반복돼도 같은 CLOSED 상태를 유지해 DELETE를 멱등하게 만든다. */
    public void close(LocalDateTime now) {
        if (status == VoiceAiSessionStatus.ACTIVE) {
            status = VoiceAiSessionStatus.CLOSED;
            closedAt = now;
        }
    }
}
