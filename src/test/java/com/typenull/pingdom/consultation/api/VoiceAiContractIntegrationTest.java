package com.typenull.pingdom.consultation.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.consultation.infrastructure.gemini.GeminiGenerateContentClient;
import com.typenull.pingdom.consultation.application.VoiceAiSessionService;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "pingdom.dev-profile.enabled=true",
        "gemini.enabled=true", "gemini.api-key=test-key", "spring.jpa.hibernate.ddl-auto=create"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class VoiceAiContractIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName
            .parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired VoiceAiSessionService service;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean GeminiGenerateContentClient provider;

    @Test
    void generatedOpenApiContainsUnionOffsetAndOperationErrors() throws Exception {
        JsonNode api = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode paths = api.path("paths");
        JsonNode send = paths.path("/voice-ai/sessions/{sessionId}/messages").path("post");
        assertThat(send.at("/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ProviderEnvelopeV1");
        JsonNode union = api.at("/components/schemas/ProviderEnvelopeV1/oneOf");
        assertThat(union.size()).isEqualTo(8);
        for (JsonNode variant : union) {
            assertThat(variant.path("additionalProperties").asBoolean(true)).isFalse();
            assertThat(variant.at("/properties/schemaVersion/enum/0").asInt()).isEqualTo(1);
            assertThat(variant.at("/properties/id/pattern").asText()).isNotBlank();
        }
        for (String code : java.util.List.of("400", "401", "403", "404", "409", "410", "429", "502", "503")) {
            assertThat(send.path("responses").has(code)).as(code).isTrue();
        }
        assertThat(paths.path("/voice-ai/sessions/{sessionId}/refresh").at("/post/responses/410/description").asText()).contains("SESSION_EXPIRED");
        assertThat(paths.path("/voice-ai/sessions/{sessionId}").at("/delete/responses").has("410")).isFalse();
        assertThat(api.at("/components/schemas/VoiceAiSessionResponse/required").toString()).contains("sessionId", "expiresAt");
        assertThat(send.at("/security/0/bearerAuth").isArray()).isTrue();
    }

    @Test
    void concurrentReplayCallsProviderOnceAndRejectsConflictingPayload() throws Exception {
        Long userId = users.saveAndFlush(User.builder().username("voice-" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@example.com").password("test").birthYear(1998)
                .language("ko").country("KR").role(UserRole.USER).build()).getId();
        String sessionId = service.create(userId).sessionId();
        JsonNode envelope = mapper.readTree("{\"schemaVersion\":1,\"id\":\"r1\",\"kind\":\"assistant_message\",\"text\":\"안내\"}");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(provider.generateEnvelope("hello", "r1")).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout");
            return envelope;
        });
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<JsonNode> first = executor.submit(() -> service.send(sessionId, userId, "hello", "r1"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            CountDownLatch attempted = new CountDownLatch(2);
            Future<JsonNode> replay = executor.submit(() -> { attempted.countDown(); return service.send(sessionId, userId, "hello", "r1"); });
            Future<JsonNode> conflict = executor.submit(() -> { attempted.countDown(); return service.send(sessionId, userId, "different", "r1"); });
            assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> replay.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(envelope);
            assertThat(replay.get(5, TimeUnit.SECONDS)).isEqualTo(envelope);
            assertThatThrownBy(() -> conflict.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(VoiceAiException.class)
                    .satisfies(error -> assertThat(((VoiceAiException) error.getCause()).getErrorCode().getCode()).isEqualTo("REPLAY_CONFLICT"));
            verify(provider, times(1)).generateEnvelope(anyString(), anyString());
            service.close(sessionId, userId);
            service.close(sessionId, userId);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void refreshAndCloseWaitForInFlightMessage(boolean close) throws Exception {
        Long userId = users.saveAndFlush(User.builder().username("voice-" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@example.com").password("test").birthYear(1998)
                .language("ko").country("KR").role(UserRole.USER).build()).getId();
        String sessionId = service.create(userId).sessionId();
        JsonNode envelope = mapper.readTree("{\"schemaVersion\":1,\"id\":\"r1\",\"kind\":\"assistant_message\",\"text\":\"안내\"}");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(provider.generateEnvelope("hello", "r1")).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test release timeout");
            return envelope;
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<JsonNode> message = executor.submit(() -> service.send(sessionId, userId, "hello", "r1"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            CountDownLatch attempted = new CountDownLatch(1);
            Future<?> mutation = executor.submit(() -> {
                attempted.countDown();
                if (close) service.close(sessionId, userId);
                else service.refresh(sessionId, userId);
            });
            assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> mutation.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            assertThat(message.get(5, TimeUnit.SECONDS)).isEqualTo(envelope);
            mutation.get(5, TimeUnit.SECONDS);
            if (close) {
                assertThatThrownBy(() -> service.send(sessionId, userId, "hello", "r1"))
                        .isInstanceOfSatisfying(VoiceAiException.class,
                                error -> assertThat(error.getErrorCode().getCode()).isEqualTo("SESSION_EXPIRED"));
            } else {
                assertThat(service.send(sessionId, userId, "hello", "r1")).isEqualTo(envelope);
            }
            verify(provider, times(1)).generateEnvelope(anyString(), anyString());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
