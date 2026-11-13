package com.typenull.pingdom.consultation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.consultation.api.dto.VoiceAiSessionResponse;
import com.typenull.pingdom.consultation.domain.VoiceAiSession;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiErrorCode;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import com.typenull.pingdom.consultation.infrastructure.gemini.GeminiProperties;
import com.typenull.pingdom.consultation.infrastructure.persistence.VoiceAiSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 인증 사용자별 5분 세션과 요청 ID별 응답 재전송 기록을 관리.
 * provider 호출 전후에만 짧은 트랜잭션을 사용한다. 외부 호출은 DB 잠금 및 커넥션 점유 범위에서 제외한다.
 */
@Service
@Slf4j
public class VoiceAiSessionService {
    private static final java.time.Duration SESSION_TTL = java.time.Duration.ofMinutes(5);
    private static final int MAX_ENVELOPE_BYTES = 16 * 1024;

    private final VoiceAiSessionRepository sessionRepository;
    private final GeminiProperties geminiProperties;
    private final GeminiVoiceClient geminiVoiceClient;
    private final Clock clock;
    private final ProviderEnvelopeValidator envelopeValidator;
    private final VoiceAiReplayTransactionService replayTransactionService;
    private final Supplier<VoiceAiReplayWait> replayWaits;

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
        this(sessionRepository, geminiProperties, geminiVoiceClient, envelopeValidator,
                replayTransactionService, clock, VoiceAiReplayWait::start);
    }

    VoiceAiSessionService(VoiceAiSessionRepository sessionRepository, GeminiProperties geminiProperties,
                          GeminiVoiceClient geminiVoiceClient,
                          ProviderEnvelopeValidator envelopeValidator,
                          VoiceAiReplayTransactionService replayTransactionService, Clock clock,
                          Supplier<VoiceAiReplayWait> replayWaits) {
        this.sessionRepository = sessionRepository;
        this.geminiProperties = geminiProperties;
        this.geminiVoiceClient = geminiVoiceClient;
        this.envelopeValidator = envelopeValidator;
        this.replayTransactionService = replayTransactionService;
        this.clock = clock;
        this.replayWaits = replayWaits;
    }

    @Transactional
    public VoiceAiSessionResponse create(Long userId) {
        LocalDateTime expiresAt = now().plus(SESSION_TTL);
        VoiceAiSession session = VoiceAiSession.create(UUID.randomUUID().toString(), userId, expiresAt);
        sessionRepository.save(session);
        return response(session.getSessionId(), expiresAt);
    }

    public VoiceAiSessionResponse refresh(String sessionId, Long userId) {
        VoiceAiReplayWait wait = replayWaits.get();
        while (true) {
            wait.beforeAttempt();
            LocalDateTime now = now();
            LocalDateTime expiresAt = now.plus(SESSION_TTL);
            try {
                if (replayTransactionService.refreshWhenNoProcessing(sessionId, userId, now, expiresAt,
                        processingLease())) {
                    return response(sessionId, expiresAt);
                }
            } catch (RuntimeException exception) {
                if (!VoiceAiReplayWait.isDatabaseWaitFailure(exception)) throw exception;
                // 프록시가 해당 시도의 트랜잭션을 롤백한 뒤 DB 연결 밖에서 대기한다.
            }
            wait.pause();
        }
    }

    public void close(String sessionId, Long userId) {
        VoiceAiReplayWait wait = replayWaits.get();
        while (true) {
            wait.beforeAttempt();
            try {
                if (replayTransactionService.closeWhenNoProcessing(sessionId, userId, now(), processingLease())) {
                    return;
                }
            } catch (RuntimeException exception) {
                if (!VoiceAiReplayWait.isDatabaseWaitFailure(exception)) throw exception;
                // 실패한 DB 시도는 소유권을 변경하지 않은 채 다시 확인한다.
            }
            wait.pause();
        }
    }

    /**
     * 짧은 트랜잭션에서 requestId별 provider 호출 소유권을 먼저 확보한 뒤 외부 호출을 수행한다.
     * 동일 요청은 결과가 저장될 때까지 잠금 없이 조회 대기하며, 만료된 소유권만 다른 요청이 인계한다.
     */
    public JsonNode send(String sessionId, Long userId, String text, String requestId) {
        String payloadHash = sha256(text);
        VoiceAiReplayWait wait = replayWaits.get();
        while (true) {
            wait.beforeAttempt();
            VoiceAiReplayClaim claim;
            try {
                claim = replayTransactionService.claim(sessionId, userId, requestId, payloadHash,
                        now(), processingLease());
            } catch (RuntimeException exception) {
                if (!VoiceAiReplayWait.isDatabaseWaitFailure(exception)) throw exception;
                wait.pause();
                continue;
            }
            if (claim.type() == VoiceAiReplayClaim.Type.COMPLETED) {
                return claim.envelope();
            }
            if (claim.type() == VoiceAiReplayClaim.Type.PROCESSING) {
                wait.pause();
                continue;
            }
            if (!geminiProperties.enabled() || !StringUtils.hasText(geminiProperties.apiKey())) {
                log.warn("Voice AI provider failure provider=gemini outcome=configuration enabled={} apiKeyPresent={}",
                        geminiProperties.enabled(), StringUtils.hasText(geminiProperties.apiKey()));
                replayTransactionService.release(sessionId, userId, requestId, claim.processingToken());
                throw new VoiceAiException(VoiceAiErrorCode.PROVIDER_UNAVAILABLE);
            }
            try {
                wait.beforeAttempt();
            } catch (VoiceAiException exception) {
                try {
                    replayTransactionService.abandonUnstarted(sessionId, userId, requestId, claim.processingToken());
                } catch (RuntimeException cleanupFailure) {
                    // 미실행 claim은 기존 lease 만료로 회수한다. 정리 장애가 원래 대기 종료를 가리지 않게 한다.
                    log.warn("Voice AI unstarted claim cleanup failed exceptionType={}",
                            cleanupFailure.getClass().getSimpleName());
                }
                throw exception;
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
                log.warn("Voice AI provider failure provider=gemini outcome=provider_call_failed exceptionType={}",
                        exception.getClass().getSimpleName());
                replayTransactionService.release(sessionId, userId, requestId, claim.processingToken());
                throw new VoiceAiException(VoiceAiErrorCode.PROVIDER_UNAVAILABLE, exception);
            }
            wait.pause();
        }
    }

    private VoiceAiSessionResponse response(String sessionId, LocalDateTime expiresAt) {
        // 기존 DB의 서버 로컬 시각 해석을 유지하고 API 경계에서 offset을 명시.
        return new VoiceAiSessionResponse(sessionId, expiresAt.atZone(clock.getZone()).toOffsetDateTime());
    }

    private void validateEnvelope(JsonNode envelope, String requestId) {
        if (envelope == null || !envelope.isObject()) invalidEnvelope("not_object", "$", envelope);
        if (envelope.toString().getBytes(StandardCharsets.UTF_8).length > MAX_ENVELOPE_BYTES) invalidEnvelope("envelope_too_large");
        if (envelope.path("schemaVersion").asInt() != 1) invalidEnvelope("schema_version", "schemaVersion", envelope.path("schemaVersion"));
        if (!requestId.equals(envelope.path("id").asText())) invalidEnvelope("request_id_mismatch", "id", envelope.path("id"));
        if (envelope.has("source") || envelope.has("command_result")) invalidEnvelope("forbidden_field");
        envelopeValidator.validate(envelope, requestId);
    }

    private void invalidEnvelope(String reason) {
        log.warn("Voice AI provider failure provider=gemini outcome=invalid_response reason={}", reason);
        throw new VoiceAiException(VoiceAiErrorCode.PROVIDER_RESPONSE_INVALID);
    }

    private void invalidEnvelope(String reason, String field, JsonNode value) {
        log.warn("Voice AI provider failure provider=gemini outcome=invalid_response reason={} field={} valueType={}",
                reason, field, value == null ? "NULL" : value.getNodeType());
        throw new VoiceAiException(VoiceAiErrorCode.PROVIDER_RESPONSE_INVALID);
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
        return VoiceAiProcessingLease.duration(geminiProperties);
    }

}
