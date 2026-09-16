package com.typenull.pingdom.consultation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.consultation.api.dto.VoiceAiEnvelopeResponse;
import com.typenull.pingdom.consultation.api.dto.VoiceAiSessionResponse;
import com.typenull.pingdom.consultation.domain.VoiceAiSession;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiErrorCode;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import com.typenull.pingdom.consultation.infrastructure.gemini.GeminiProperties;
import com.typenull.pingdom.consultation.infrastructure.persistence.VoiceAiSessionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class VoiceAiSessionService {
    private static final java.time.Duration SESSION_TTL = java.time.Duration.ofMinutes(5);
    private static final int MAX_ENVELOPE_BYTES = 16 * 1024;

    private final VoiceAiSessionRepository sessionRepository;
    private final GeminiProperties geminiProperties;
    private final GeminiVoiceClient geminiVoiceClient;
    private final Clock clock;

    public VoiceAiSessionService(VoiceAiSessionRepository sessionRepository, GeminiProperties geminiProperties,
                                 GeminiVoiceClient geminiVoiceClient) {
        this(sessionRepository, geminiProperties, geminiVoiceClient, Clock.systemDefaultZone());
    }

    VoiceAiSessionService(VoiceAiSessionRepository sessionRepository, GeminiProperties geminiProperties,
                          GeminiVoiceClient geminiVoiceClient, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.geminiProperties = geminiProperties;
        this.geminiVoiceClient = geminiVoiceClient;
        this.clock = clock;
    }

    @Transactional
    public VoiceAiSessionResponse create(Long userId) {
        LocalDateTime expiresAt = now().plus(SESSION_TTL);
        VoiceAiSession session = VoiceAiSession.create(UUID.randomUUID().toString(), userId, expiresAt);
        sessionRepository.save(session);
        return new VoiceAiSessionResponse(session.getSessionId(), expiresAt);
    }

    @Transactional
    public VoiceAiSessionResponse refresh(String sessionId, Long userId) {
        VoiceAiSession session = requireSession(sessionId, userId);
        requireUsable(session);
        LocalDateTime expiresAt = now().plus(SESSION_TTL);
        session.refresh(expiresAt);
        return new VoiceAiSessionResponse(sessionId, expiresAt);
    }

    @Transactional
    public void close(String sessionId, Long userId) {
        requireSession(sessionId, userId).close(now());
    }

    @Transactional(readOnly = true)
    public VoiceAiEnvelopeResponse send(String sessionId, Long userId, String text, String requestId) {
        VoiceAiSession session = requireSession(sessionId, userId);
        requireUsable(session);
        if (!geminiProperties.enabled() || !StringUtils.hasText(geminiProperties.apiKey())) {
            throw new VoiceAiException(VoiceAiErrorCode.PROVIDER_UNAVAILABLE);
        }
        try {
            JsonNode envelope = geminiVoiceClient.generateEnvelope(text, requestId);
            validateEnvelope(envelope, requestId);
            return new VoiceAiEnvelopeResponse(envelope);
        } catch (VoiceAiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new VoiceAiException(VoiceAiErrorCode.PROVIDER_UNAVAILABLE, exception);
        }
    }

    private VoiceAiSession requireSession(String sessionId, Long userId) {
        return sessionRepository.findById(sessionId)
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
                || !isAllowedKind(envelope.path("kind").asText())
                || envelope.has("source") || envelope.has("command_result")) {
            throw new VoiceAiException(VoiceAiErrorCode.PROVIDER_RESPONSE_INVALID);
        }
    }

    private boolean isAllowedKind(String kind) {
        return "command_request".equals(kind) || "clarification_request".equals(kind)
                || "assistant_message".equals(kind) || "protocol_error".equals(kind);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
