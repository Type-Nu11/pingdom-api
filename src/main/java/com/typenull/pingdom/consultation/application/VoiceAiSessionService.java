package com.typenull.pingdom.consultation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.consultation.api.dto.VoiceAiSessionResponse;
import com.typenull.pingdom.consultation.domain.VoiceAiSession;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiErrorCode;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import com.typenull.pingdom.consultation.infrastructure.gemini.GeminiProperties;
import com.typenull.pingdom.consultation.infrastructure.persistence.VoiceAiSessionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.concurrent.locks.LockSupport;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 인증 사용자별 5분 세션과 요청 ID별 응답 재전송 기록을 관리.
 * provider 호출 전후에만 짧은 트랜잭션을 사용한다. 외부 호출은 DB 잠금 및 커넥션 점유 범위에서 제외한다.
 */
@Service
public class VoiceAiSessionService {
    private static final java.time.Duration SESSION_TTL = java.time.Duration.ofMinutes(5);
    private static final int MAX_ENVELOPE_BYTES = 16 * 1024;

    private final VoiceAiSessionRepository sessionRepository;
    private final GeminiProperties geminiProperties;
    private final GeminiVoiceClient geminiVoiceClient;
    private final Clock clock;
    private final ProviderEnvelopeValidator envelopeValidator;
    private final VoiceAiReplayTransactionService replayTransactionService;

    @Autowired
    public VoiceAiSessionService(VoiceAiSessionRepository sessionRepository, GeminiProperties geminiProperties,
                                 GeminiVoiceClient geminiVoiceClient,
                                 ProviderEnvelopeValidator envelopeValidator,
                                 VoiceAiReplayTransactionService replayTransactionService) {
        this(sessionRepository, geminiProperties, geminiVoiceClient, envelopeValidator,
                replayTransactionService, Clock.systemDefaultZone());
    }

    VoiceAiSessionService(VoiceAiSessionRepository sessionRepository, GeminiProperties geminiProperties,
                          GeminiVoiceClient geminiVoiceClient,
                          ProviderEnvelopeValidator envelopeValidator,
                          VoiceAiReplayTransactionService replayTransactionService, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.geminiProperties = geminiProperties;
        this.geminiVoiceClient = geminiVoiceClient;
        this.envelopeValidator = envelopeValidator;
        this.replayTransactionService = replayTransactionService;
        this.clock = clock;
    }

    @Transactional
    public VoiceAiSessionResponse create(Long userId) {
        LocalDateTime expiresAt = now().plus(SESSION_TTL);
        VoiceAiSession session = VoiceAiSession.create(UUID.randomUUID().toString(), userId, expiresAt);
        sessionRepository.save(session);
        return response(session.getSessionId(), expiresAt);
    }

    public VoiceAiSessionResponse refresh(String sessionId, Long userId) {
        while (true) {
            LocalDateTime now = now();
            LocalDateTime expiresAt = now.plus(SESSION_TTL);
            if (replayTransactionService.refreshWhenNoProcessing(sessionId, userId, now, expiresAt,
                    processingLease())) {
                return response(sessionId, expiresAt);
            }
            waitForReplayCompletion();
        }
    }

    public void close(String sessionId, Long userId) {
        while (!replayTransactionService.closeWhenNoProcessing(sessionId, userId, now(), processingLease())) {
            waitForReplayCompletion();
        }
    }

    /**
     * 짧은 트랜잭션에서 requestId별 provider 호출 소유권을 먼저 확보한 뒤 외부 호출을 수행한다.
     * 동일 요청은 결과가 저장될 때까지 잠금 없이 조회 대기하며, 만료된 소유권만 다른 요청이 인계한다.
     */
    public JsonNode send(String sessionId, Long userId, String text, String requestId) {
        String payloadHash = sha256(text);
        while (true) {
            VoiceAiReplayClaim claim = replayTransactionService.claim(sessionId, userId, requestId, payloadHash,
                    now(), processingLease());
            if (claim.type() == VoiceAiReplayClaim.Type.COMPLETED) {
                return claim.envelope();
            }
            if (claim.type() == VoiceAiReplayClaim.Type.PROCESSING) {
                waitForReplayCompletion();
                continue;
            }
            if (!geminiProperties.enabled() || !StringUtils.hasText(geminiProperties.apiKey())) {
                replayTransactionService.release(sessionId, userId, requestId, claim.processingToken());
                throw new VoiceAiException(VoiceAiErrorCode.PROVIDER_UNAVAILABLE);
            }
            try {
                JsonNode envelope = geminiVoiceClient.generateEnvelope(text, requestId);
                validateEnvelope(envelope, requestId);
                JsonNode completed = replayTransactionService.complete(sessionId, userId, requestId,
                        claim.processingToken(), envelope);
                if (completed != null) {
                    return completed;
                }
            } catch (VoiceAiException exception) {
                replayTransactionService.release(sessionId, userId, requestId, claim.processingToken());
                throw exception;
            } catch (RuntimeException exception) {
                replayTransactionService.release(sessionId, userId, requestId, claim.processingToken());
                throw new VoiceAiException(VoiceAiErrorCode.PROVIDER_UNAVAILABLE, exception);
            }
        }
    }

    private VoiceAiSessionResponse response(String sessionId, LocalDateTime expiresAt) {
        // 기존 DB의 서버 로컬 시각 해석을 유지하고 API 경계에서 offset을 명시.
        return new VoiceAiSessionResponse(sessionId, expiresAt.atZone(clock.getZone()).toOffsetDateTime());
    }

    private void validateEnvelope(JsonNode envelope, String requestId) {
        if (envelope == null || !envelope.isObject()
                || envelope.toString().getBytes(StandardCharsets.UTF_8).length > MAX_ENVELOPE_BYTES
                || envelope.path("schemaVersion").asInt() != 1
                || !requestId.equals(envelope.path("id").asText())
                || envelope.has("source") || envelope.has("command_result")) {
            throw new VoiceAiException(VoiceAiErrorCode.PROVIDER_RESPONSE_INVALID);
        }
        envelopeValidator.validate(envelope, requestId);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private Duration processingLease() {
        Duration providerTimeout = geminiProperties.connectTimeout().plus(geminiProperties.readTimeout()).plusSeconds(5);
        return providerTimeout.compareTo(Duration.ofSeconds(30)) < 0 ? Duration.ofSeconds(30) : providerTimeout;
    }

    private void waitForReplayCompletion() {
        // DB 연결을 잡지 않은 채 재전송 결과만 확인한다. HTTP 동기 응답 계약을 유지하기 위한 짧은 polling이다.
        LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
    }
}
