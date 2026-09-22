package com.typenull.pingdom.consultation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.consultation.domain.VoiceAiReplay;
import com.typenull.pingdom.consultation.domain.VoiceAiReplayStatus;
import com.typenull.pingdom.consultation.domain.VoiceAiSession;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiErrorCode;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import com.typenull.pingdom.consultation.infrastructure.persistence.VoiceAiReplayRepository;
import com.typenull.pingdom.consultation.infrastructure.persistence.VoiceAiSessionRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * provider 호출 전후의 DB 변경만 짧게 처리한다.
 * 모든 변경은 세션 행 잠금 안에서 이뤄져 requestId별 소유권과 세션 종료 순서를 보장한다.
 */
@Service
class VoiceAiReplayTransactionService {
    private final VoiceAiSessionRepository sessionRepository;
    private final VoiceAiReplayRepository replayRepository;

    VoiceAiReplayTransactionService(VoiceAiSessionRepository sessionRepository,
                                    VoiceAiReplayRepository replayRepository) {
        this.sessionRepository = sessionRepository;
        this.replayRepository = replayRepository;
    }

    @Transactional
    public VoiceAiReplayClaim claim(String sessionId, Long userId, String requestId, String payloadHash,
                                    LocalDateTime now, Duration processingLease) {
        requireUsableSession(sessionId, userId, now);
        VoiceAiReplay replay = replayRepository.findBySessionIdAndRequestId(sessionId, requestId).orElse(null);
        if (replay != null && !replay.hasSamePayload(payloadHash)) {
            throw new VoiceAiException(VoiceAiErrorCode.REPLAY_CONFLICT);
        }
        if (replay != null && replay.isCompleted()) {
            return VoiceAiReplayClaim.completed(replay.getEnvelope());
        }
        if (replay != null && replay.isProcessingLeaseExpiredAt(now, processingLease)) {
            String token = newToken();
            replay.takeOverProcessing(token, now);
            return VoiceAiReplayClaim.owner(token);
        }
        if (replay != null) {
            return VoiceAiReplayClaim.processing();
        }

        VoiceAiReplay activeProcessing = findProcessing(sessionId);
        if (activeProcessing != null && !activeProcessing.isProcessingLeaseExpiredAt(now, processingLease)) {
            // requestId가 달라도 세션당 provider 호출은 하나만 유지해 갱신·종료 순서를 보존한다.
            return VoiceAiReplayClaim.processing();
        }
        if (activeProcessing != null) {
            replayRepository.delete(activeProcessing);
        }
        String token = newToken();
        replayRepository.save(VoiceAiReplay.start(sessionId, requestId, payloadHash, token, now));
        return VoiceAiReplayClaim.owner(token);
    }

    @Transactional
    public JsonNode complete(String sessionId, Long userId, String requestId, String processingToken,
                             JsonNode envelope) {
        requireOwnedSession(sessionId, userId);
        VoiceAiReplay replay = replayRepository.findBySessionIdAndRequestId(sessionId, requestId).orElse(null);
        if (replay != null && replay.complete(processingToken, envelope)) {
            return envelope;
        }
        return replay != null && replay.isCompleted() ? replay.getEnvelope() : null;
    }

    @Transactional
    public void release(String sessionId, Long userId, String requestId, String processingToken) {
        requireOwnedSession(sessionId, userId);
        replayRepository.findBySessionIdAndRequestId(sessionId, requestId)
                .filter(replay -> replay.isClaimedBy(processingToken))
                .ifPresent(replayRepository::delete);
    }

    @Transactional
    public boolean refreshWhenNoProcessing(String sessionId, Long userId, LocalDateTime now,
                                           LocalDateTime expiresAt, Duration processingLease) {
        VoiceAiSession session = requireUsableSession(sessionId, userId, now);
        if (hasActiveProcessing(sessionId, now, processingLease)) {
            return false;
        }
        session.refresh(expiresAt);
        return true;
    }

    @Transactional
    public boolean closeWhenNoProcessing(String sessionId, Long userId, LocalDateTime now,
                                         Duration processingLease) {
        VoiceAiSession session = requireOwnedSession(sessionId, userId);
        VoiceAiReplay processing = findProcessing(sessionId);
        if (processing != null) {
            if (!processing.isProcessingLeaseExpiredAt(now, processingLease)) {
                return false;
            }
            // 종료는 더 이상 결과를 수신하지 않으므로 죽은 worker의 stale 소유권을 제거한다.
            replayRepository.delete(processing);
        }
        session.close(now);
        return true;
    }

    private boolean hasActiveProcessing(String sessionId, LocalDateTime now, Duration processingLease) {
        VoiceAiReplay processing = findProcessing(sessionId);
        if (processing == null) {
            return false;
        }
        if (processing.isProcessingLeaseExpiredAt(now, processingLease)) {
            replayRepository.delete(processing);
            return false;
        }
        return true;
    }

    private VoiceAiSession requireUsableSession(String sessionId, Long userId, LocalDateTime now) {
        VoiceAiSession session = requireOwnedSession(sessionId, userId);
        if (!session.isUsableAt(now)) {
            throw new VoiceAiException(VoiceAiErrorCode.SESSION_EXPIRED);
        }
        return session;
    }

    private VoiceAiReplay findProcessing(String sessionId) {
        return replayRepository.findFirstBySessionIdAndStatusOrderByCreatedAtDesc(sessionId, VoiceAiReplayStatus.PROCESSING)
                .orElse(null);
    }

    private VoiceAiSession requireOwnedSession(String sessionId, Long userId) {
        return sessionRepository.findByIdForUpdate(sessionId)
                .map(session -> {
                    if (!session.belongsTo(userId)) {
                        throw new VoiceAiException(VoiceAiErrorCode.SESSION_FORBIDDEN);
                    }
                    return session;
                })
                .orElseThrow(() -> new VoiceAiException(VoiceAiErrorCode.SESSION_NOT_FOUND));
    }

    private String newToken() {
        return UUID.randomUUID().toString();
    }
}
