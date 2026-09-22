package com.typenull.pingdom.consultation.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.typenull.pingdom.consultation.domain.VoiceAiReplay;
import com.typenull.pingdom.consultation.domain.VoiceAiReplayStatus;
import com.typenull.pingdom.consultation.domain.VoiceAiSession;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import com.typenull.pingdom.consultation.infrastructure.gemini.GeminiProperties;
import com.typenull.pingdom.consultation.infrastructure.persistence.VoiceAiReplayRepository;
import com.typenull.pingdom.consultation.infrastructure.persistence.VoiceAiSessionRepository;
import java.time.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoiceAiSessionServiceTest {
    private final VoiceAiSessionRepository sessions = mock(VoiceAiSessionRepository.class);
    private final VoiceAiReplayRepository replays = mock(VoiceAiReplayRepository.class);
    private final GeminiVoiceClient provider = mock(GeminiVoiceClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-17T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    private final Map<String, VoiceAiReplay> replayStore = new HashMap<>();
    private final VoiceAiReplayTransactionService transactions = new VoiceAiReplayTransactionService(sessions, replays);
    private final VoiceAiSessionService service = new VoiceAiSessionService(sessions,
            new GeminiProperties(true, "test-key", null, null, null), provider, new ProviderEnvelopeValidator(),
            transactions, clock);
    private VoiceAiSession session;

    /**
     * 사용자 1 소유의 5분 유효 세션을 만들고 ID 잠금 조회 결과를 고정.
     */
    @BeforeEach
    void setup() {
        session = VoiceAiSession.create("session", 1L, LocalDateTime.now(clock).plusMinutes(5));
        when(sessions.findByIdForUpdate("session")).thenReturn(Optional.of(session));
        when(replays.findBySessionIdAndRequestId(eq("session"), anyString()))
                .thenAnswer(call -> Optional.ofNullable(replayStore.get(call.getArgument(1))));
        when(replays.findFirstBySessionIdAndStatusOrderByCreatedAtDesc("session", VoiceAiReplayStatus.PROCESSING))
                .thenAnswer(call -> replayStore.values().stream()
                        .filter(replay -> replay.getStatus() == VoiceAiReplayStatus.PROCESSING)
                        .findFirst());
        when(replays.save(any())).thenAnswer(call -> {
            VoiceAiReplay replay = call.getArgument(0);
            replayStore.put(replay.getRequestId(), replay);
            return replay;
        });
        doAnswer(call -> {
            VoiceAiReplay replay = call.getArgument(0);
            replayStore.remove(replay.getRequestId());
            return null;
        }).when(replays).delete(any(VoiceAiReplay.class));
    }

    /**
     * 세션 생성·갱신 JSON이 비어 있지 않은 ID와 +09:00 오프셋을 포함한 동일 만료 시각을 제공하는지 검증.
     */
    @Test
    void serializesSessionExpiryOffset() throws Exception {
        for (var response : java.util.List.of(service.create(1L), service.refresh("session", 1L))) {
            var json = mapper.readTree(mapper.writeValueAsString(response));
            assertThat(OffsetDateTime.parse(json.path("expiresAt").asText()))
                    .isEqualTo(OffsetDateTime.parse("2026-09-17T12:05:00+09:00"));
            assertThat(json.path("sessionId").asText()).isNotBlank();
        }
    }

    /**
     * 동일 요청 ID·텍스트는 저장된 envelope를 재사용하고 텍스트 변경은 REPLAY_CONFLICT이며 공급자 호출은 한 번인지 검증.
     * 세션 종료 후에는 기존 replay도 SESSION_EXPIRED로 거절. mock 기반 테스트로 실제 DB 커밋 검증은 제외.
     */
    @Test
    void replaysOnlyMatchingSessionRequest() throws Exception {
        JsonNode envelope = mapper.readTree("{\"schemaVersion\":1,\"id\":\"r1\",\"kind\":\"assistant_message\",\"text\":\"안내\"}");
        when(provider.generateEnvelope("hello", "r1")).thenReturn(envelope);
        assertThat(service.send("session", 1L, "hello", "r1")).isEqualTo(envelope);
        assertThat(service.send("session", 1L, "hello", "r1")).isEqualTo(envelope);
        assertCode(() -> service.send("session", 1L, "changed", "r1"), "REPLAY_CONFLICT");
        verify(provider, times(1)).generateEnvelope(anyString(), anyString());
        service.close("session", 1L);
        assertCode(() -> service.send("session", 1L, "hello", "r1"), "SESSION_EXPIRED");
    }

    /**
     * 없는 세션·다른 소유자·만료 경계를 각각 식별하고 반복 종료를 허용하며 공급자를 호출하지 않는지 검증.
     */
    @Test
    void checksSessionBoundariesAndClosure() {
        assertCode(() -> service.refresh("missing", 1L), "SESSION_NOT_FOUND");
        assertCode(() -> service.refresh("session", 2L), "SESSION_FORBIDDEN");
        session.refresh(LocalDateTime.now(clock));
        assertCode(() -> service.refresh("session", 1L), "SESSION_EXPIRED");
        service.close("session", 1L);
        service.close("session", 1L);
        verifyNoInteractions(provider);
    }

    /**
     * 공급자 실패·잘못된 envelope는 502로 변환하고 PROCESSING 소유권을 해제해 같은 requestId를 재시도할 수 있는지 검증.
     */
    @Test
    void skipsFailedProviderReplayStorage() throws Exception {
        when(provider.generateEnvelope(anyString(), anyString())).thenThrow(new RuntimeException("timeout"));
        assertCode(() -> service.send("session", 1L, "hello", "r1"), "PROVIDER_UNAVAILABLE");
        reset(provider);
        when(provider.generateEnvelope(anyString(), anyString())).thenReturn(mapper.readTree("{\"id\":\"wrong\"}"));
        assertCode(() -> service.send("session", 1L, "hello", "r1"), "PROVIDER_RESPONSE_INVALID");
        assertThat(replayStore).isEmpty();
    }

    /**
     * 한글 6,000자의 과대 envelope가 PROVIDER_RESPONSE_INVALID로 거절되고 replay를 저장하지 않는지 검증.
     */
    @Test
    void rejectsOversizedEnvelopeStorage() {
        var envelope = mapper.createObjectNode().put("schemaVersion", 1).put("id", "r1")
                .put("kind", "assistant_message").put("text", "가".repeat(6_000));
        when(provider.generateEnvelope("hello", "r1")).thenReturn(envelope);
        assertCode(() -> service.send("session", 1L, "hello", "r1"), "PROVIDER_RESPONSE_INVALID");
        assertThat(replayStore).isEmpty();
    }

    /**
     * 프로세스 중단처럼 기존 worker가 lease를 넘긴 경우 새 worker만 결과를 확정하고,
     * 늦게 도착한 기존 worker의 결과는 덮어쓰지 못하는지 검증.
     */
    @Test
    void fencesLateProviderResultAfterProcessingLeaseTakeover() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        var first = transactions.claim("session", 1L, "r1", "hash", now, Duration.ofSeconds(30));
        var second = transactions.claim("session", 1L, "r1", "hash", now.plusSeconds(30), Duration.ofSeconds(30));
        JsonNode oldEnvelope = mapper.readTree("{\"schemaVersion\":1,\"id\":\"r1\",\"kind\":\"assistant_message\",\"text\":\"old\"}");
        JsonNode newEnvelope = mapper.readTree("{\"schemaVersion\":1,\"id\":\"r1\",\"kind\":\"assistant_message\",\"text\":\"new\"}");

        assertThat(first.type()).isEqualTo(VoiceAiReplayClaim.Type.OWNER);
        assertThat(second.type()).isEqualTo(VoiceAiReplayClaim.Type.OWNER);
        assertThat(transactions.complete("session", 1L, "r1", first.processingToken(), oldEnvelope)).isNull();
        assertThat(transactions.complete("session", 1L, "r1", second.processingToken(), newEnvelope)).isEqualTo(newEnvelope);
        assertThat(replayStore.get("r1").getEnvelope()).isEqualTo(newEnvelope);
    }

    /** 같은 세션의 다른 requestId도 선행 provider 호출이 끝날 때까지 소유권을 얻지 못하는지 검증. */
    @Test
    void serializesDifferentRequestsInSameSessionWhileProviderIsProcessing() {
        LocalDateTime now = LocalDateTime.now(clock);
        var first = transactions.claim("session", 1L, "r1", "hash-1", now, Duration.ofSeconds(30));
        var later = transactions.claim("session", 1L, "r2", "hash-2", now, Duration.ofSeconds(30));

        assertThat(first.type()).isEqualTo(VoiceAiReplayClaim.Type.OWNER);
        assertThat(later.type()).isEqualTo(VoiceAiReplayClaim.Type.PROCESSING);
    }

    /**
     * 주어진 동작이 VoiceAiException을 던지고 기대 오류 코드를 포함하는지 공통으로 확인.
     */
    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(VoiceAiException.class,
                exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo(code));
    }
}
