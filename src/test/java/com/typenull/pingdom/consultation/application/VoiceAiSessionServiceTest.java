package com.typenull.pingdom.consultation.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.typenull.pingdom.consultation.domain.VoiceAiReplay;
import com.typenull.pingdom.consultation.domain.VoiceAiSession;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import com.typenull.pingdom.consultation.infrastructure.gemini.GeminiProperties;
import com.typenull.pingdom.consultation.infrastructure.persistence.VoiceAiReplayRepository;
import com.typenull.pingdom.consultation.infrastructure.persistence.VoiceAiSessionRepository;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoiceAiSessionServiceTest {
    private final VoiceAiSessionRepository sessions = mock(VoiceAiSessionRepository.class);
    private final VoiceAiReplayRepository replays = mock(VoiceAiReplayRepository.class);
    private final GeminiVoiceClient provider = mock(GeminiVoiceClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-17T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    private final VoiceAiSessionService service = new VoiceAiSessionService(sessions, replays,
            new GeminiProperties(true, "test-key", null, null, null), provider, new ProviderEnvelopeValidator(), clock);
    private VoiceAiSession session;

    @BeforeEach
    void setup() {
        session = VoiceAiSession.create("session", 1L, LocalDateTime.now(clock).plusMinutes(5));
        when(sessions.findByIdForUpdate("session")).thenReturn(Optional.of(session));
    }

    @Test
    void createAndRefreshSerializeExplicitOffset() throws Exception {
        for (var response : java.util.List.of(service.create(1L), service.refresh("session", 1L))) {
            var json = mapper.readTree(mapper.writeValueAsString(response));
            assertThat(OffsetDateTime.parse(json.path("expiresAt").asText()))
                    .isEqualTo(OffsetDateTime.parse("2026-09-17T12:05:00+09:00"));
            assertThat(json.path("sessionId").asText()).isNotBlank();
        }
    }

    @Test
    void replayUsesCommittedResultAndRejectsChangedText() throws Exception {
        JsonNode envelope = mapper.readTree("{\"schemaVersion\":1,\"id\":\"r1\",\"kind\":\"assistant_message\",\"text\":\"안내\"}");
        when(provider.generateEnvelope("hello", "r1")).thenReturn(envelope);
        when(replays.save(any())).thenAnswer(call -> {
            VoiceAiReplay saved = call.getArgument(0);
            when(replays.findBySessionIdAndRequestId("session", "r1")).thenReturn(Optional.of(saved));
            return saved;
        });
        assertThat(service.send("session", 1L, "hello", "r1")).isEqualTo(envelope);
        assertThat(service.send("session", 1L, "hello", "r1")).isEqualTo(envelope);
        assertCode(() -> service.send("session", 1L, "changed", "r1"), "REPLAY_CONFLICT");
        verify(provider, times(1)).generateEnvelope(anyString(), anyString());
        service.close("session", 1L);
        assertCode(() -> service.send("session", 1L, "hello", "r1"), "SESSION_EXPIRED");
    }

    @Test
    void sessionBoundariesAndRepeatedClose() {
        assertCode(() -> service.refresh("missing", 1L), "SESSION_NOT_FOUND");
        assertCode(() -> service.refresh("session", 2L), "SESSION_FORBIDDEN");
        session.refresh(LocalDateTime.now(clock));
        assertCode(() -> service.refresh("session", 1L), "SESSION_EXPIRED");
        service.close("session", 1L);
        service.close("session", 1L);
        verifyNoInteractions(provider);
    }

    @Test
    void providerFailureAndInvalidEnvelopeAreNotStored() throws Exception {
        when(provider.generateEnvelope(anyString(), anyString())).thenThrow(new RuntimeException("timeout"));
        assertCode(() -> service.send("session", 1L, "hello", "r1"), "PROVIDER_UNAVAILABLE");
        reset(provider);
        when(provider.generateEnvelope(anyString(), anyString())).thenReturn(mapper.readTree("{\"id\":\"wrong\"}"));
        assertCode(() -> service.send("session", 1L, "hello", "r1"), "PROVIDER_RESPONSE_INVALID");
        verify(replays, never()).save(any());
    }

    @Test
    void oversizedEnvelopeIsRejectedBeforePersistence() {
        var envelope = mapper.createObjectNode().put("schemaVersion", 1).put("id", "r1")
                .put("kind", "assistant_message").put("text", "가".repeat(6_000));
        when(provider.generateEnvelope("hello", "r1")).thenReturn(envelope);
        assertCode(() -> service.send("session", 1L, "hello", "r1"), "PROVIDER_RESPONSE_INVALID");
        verify(replays, never()).save(any());
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(VoiceAiException.class,
                exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo(code));
    }
}
