package com.typenull.pingdom.consultation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.consultation.api.dto.VoiceAiSessionResponse;
import com.typenull.pingdom.consultation.domain.VoiceAiSession;
import com.typenull.pingdom.consultation.domain.VoiceAiReplay;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiErrorCode;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import com.typenull.pingdom.consultation.infrastructure.gemini.GeminiProperties;
import com.typenull.pingdom.consultation.infrastructure.persistence.VoiceAiSessionRepository;
import com.typenull.pingdom.consultation.infrastructure.persistence.VoiceAiReplayRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class VoiceAiSessionService {
    private static final java.time.Duration SESSION_TTL = java.time.Duration.ofMinutes(5);
    private static final int MAX_ENVELOPE_BYTES = 16 * 1024;

    private final VoiceAiSessionRepository sessionRepository;
    private final VoiceAiReplayRepository replayRepository;
    private final GeminiProperties geminiProperties;
    private final GeminiVoiceClient geminiVoiceClient;
    private final Clock clock;
    private final ProviderEnvelopeValidator envelopeValidator;

    @Autowired
    public VoiceAiSessionService(VoiceAiSessionRepository sessionRepository, VoiceAiReplayRepository replayRepository,
                                 GeminiProperties geminiProperties, GeminiVoiceClient geminiVoiceClient,
                                 ProviderEnvelopeValidator envelopeValidator) {
        this(sessionRepository, replayRepository, geminiProperties, geminiVoiceClient, envelopeValidator, Clock.systemDefaultZone());
    }

    VoiceAiSessionService(VoiceAiSessionRepository sessionRepository, VoiceAiReplayRepository replayRepository,
                          GeminiProperties geminiProperties, GeminiVoiceClient geminiVoiceClient,
                          ProviderEnvelopeValidator envelopeValidator, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.replayRepository = replayRepository;
        this.geminiProperties = geminiProperties;
        this.geminiVoiceClient = geminiVoiceClient;
        this.envelopeValidator = envelopeValidator;
        this.clock = clock;
    }

    @Transactional
    public VoiceAiSessionResponse create(Long userId) {
        LocalDateTime expiresAt = now().plus(SESSION_TTL);
        VoiceAiSession session = VoiceAiSession.create(UUID.randomUUID().toString(), userId, expiresAt);
        sessionRepository.save(session);
        return response(session.getSessionId(), expiresAt);
    }

    @Transactional
    public VoiceAiSessionResponse refresh(String sessionId, Long userId) {
        VoiceAiSession session = requireSession(sessionId, userId);
        requireUsable(session);
        LocalDateTime expiresAt = now().plus(SESSION_TTL);
        session.refresh(expiresAt);
        return response(sessionId, expiresAt);
    }

    @Transactional
    public void close(String sessionId, Long userId) {
        requireSession(sessionId, userId).close(now());
    }

    @Transactional
    public JsonNode send(String sessionId, Long userId, String text, String requestId) {
        VoiceAiSession session = requireSession(sessionId, userId);
        requireUsable(session);
        String payloadHash = sha256(text);
        VoiceAiReplay replay = replayRepository.findBySessionIdAndRequestId(sessionId, requestId).orElse(null);
        if (replay != null) {
            if (!replay.hasSamePayload(payloadHash)) {
                throw new VoiceAiException(VoiceAiErrorCode.REPLAY_CONFLICT);
            }
            return replay.getEnvelope();
        }
        if (!geminiProperties.enabled() || !StringUtils.hasText(geminiProperties.apiKey())) {
            throw new VoiceAiException(VoiceAiErrorCode.PROVIDER_UNAVAILABLE);
        }
        try {
            JsonNode envelope = geminiVoiceClient.generateEnvelope(text, requestId);
            validateEnvelope(envelope, requestId);
            replayRepository.save(VoiceAiReplay.create(sessionId, requestId, payloadHash, envelope, now()));
            return envelope;
        } catch (VoiceAiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new VoiceAiException(VoiceAiErrorCode.PROVIDER_UNAVAILABLE, exception);
        }
    }

    private VoiceAiSessionResponse response(String sessionId, LocalDateTime expiresAt) {
        // 기존 DB의 서버 로컬 시각 해석을 유지하고 API 경계에서 offset을 명시한다.
        return new VoiceAiSessionResponse(sessionId, expiresAt.atZone(clock.getZone()).toOffsetDateTime());
    }

    // provider 처리와 replay 저장이 커밋될 때까지 갱신·종료·후속 전송도 같은 행에서 대기한다.
    private VoiceAiSession requireSession(String sessionId, Long userId) {
        return sessionRepository.findByIdForUpdate(sessionId)
                .map(session -> {
                    if (!session.belongsTo(userId)) {
                        throw new VoiceAiException(VoiceAiErrorCode.SESSION_FORBIDDEN);
                    }
                    return session;
                })
                .orElseThrow(() -> new VoiceAiException(VoiceAiErrorCode.SESSION_NOT_FOUND));
    }

    private void requireUsable(VoiceAiSession session) {
        if (!session.isUsableAt(now())) {
            throw new VoiceAiException(VoiceAiErrorCode.SESSION_EXPIRED);
        }
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
}
