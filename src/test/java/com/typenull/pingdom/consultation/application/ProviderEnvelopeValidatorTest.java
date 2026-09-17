package com.typenull.pingdom.consultation.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import org.junit.jupiter.api.Test;

class ProviderEnvelopeValidatorTest {
    private final ProviderEnvelopeValidator validator = new ProviderEnvelopeValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsAssistantMessageEnvelope() throws Exception {
        var envelope = objectMapper.readTree("""
                {"schemaVersion":1,"id":"request-1","kind":"assistant_message","text":"무엇을 찾고 계신가요?"}
                """);

        assertThatCode(() -> validator.validate(envelope, "request-1")).doesNotThrowAnyException();
    }

    @Test
    void rejectsCommandWithoutRequiredArgs() throws Exception {
        var envelope = objectMapper.readTree("""
                {"schemaVersion":1,"id":"request-1","kind":"command_request","command":"getPlaceDetails","args":{}}
                """);

        assertThatThrownBy(() -> validator.validate(envelope, "request-1"))
                .isInstanceOf(VoiceAiException.class);
    }

    @Test
    void acceptsSearchWithoutOptionalCategory() throws Exception {
        var envelope = objectMapper.readTree("""
                {"schemaVersion":1,"id":"request-1","kind":"command_request","command":"searchNearbyReservablePlaces","args":{"date":"2026-09-20","startTime":"14:00","endTime":"17:00","quantity":2,"useCurrentLocation":true}}
                """);

        assertThatCode(() -> validator.validate(envelope, "request-1")).doesNotThrowAnyException();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "{\"schemaVersion\":1,\"id\":\"r1\",\"kind\":\"clarification_request\",\"field\":\"quantity\",\"text\":\"몇 명인가요?\"}",
        "{\"schemaVersion\":1,\"id\":\"r1\",\"kind\":\"protocol_error\",\"code\":\"INVALID_RESPONSE\"}",
        "{\"schemaVersion\":1,\"id\":\"r1\",\"kind\":\"command_request\",\"command\":\"cancelVoiceSession\",\"args\":{}}"
    })
    void acceptsRemainingEnvelopeKinds(String json) throws Exception {
        assertThatCode(() -> validator.validate(objectMapper.readTree(json), "r1")).doesNotThrowAnyException();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"1.5", "\"1\"", "true", "4294967297"})
    void rejectsNonV1IntegerVersion(String version) throws Exception {
        var node = objectMapper.readTree("{\"schemaVersion\":" + version + ",\"id\":\"r1\",\"kind\":\"assistant_message\",\"text\":\"ok\"}");
        assertThatThrownBy(() -> validator.validate(node, "r1")).isInstanceOf(VoiceAiException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"1.5", "0", "9007199254740992", "\"1\""})
    void rejectsInvalidResourceId(String id) throws Exception {
        var node = objectMapper.readTree("{\"schemaVersion\":1,\"id\":\"r1\",\"kind\":\"command_request\",\"command\":\"getPlaceDetails\",\"args\":{\"placeId\":" + id + "}}");
        assertThatThrownBy(() -> validator.validate(node, "r1")).isInstanceOf(VoiceAiException.class);
    }

    @Test
    void acceptsMathematicallyIntegralJsonNumbers() throws Exception {
        var node = objectMapper.readTree("{\"schemaVersion\":1.0,\"id\":\"r1\",\"kind\":\"command_request\",\"command\":\"getPlaceDetails\",\"args\":{\"placeId\":1.0}}");
        assertThatCode(() -> validator.validate(node, "r1")).doesNotThrowAnyException();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "{\"command\":\"getAvailabilities\",\"args\":{\"placeId\":1,\"date\":\"2026-09-20\",\"quantity\":12}}",
        "{\"command\":\"prepareReservation\",\"args\":{\"placeId\":1,\"availabilityId\":2,\"quantity\":1}}"
    })
    void acceptsRemainingCommands(String fields) throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(fields);
        node.put("schemaVersion", 1).put("id", "r1").put("kind", "command_request");
        assertThatCode(() -> validator.validate(node, "r1")).doesNotThrowAnyException();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"0000-01-01", "2026-02-29", "+10000-01-01"})
    void rejectsDatesOutsideAppContract(String date) throws Exception {
        var node = objectMapper.readTree("{\"schemaVersion\":1,\"id\":\"r1\",\"kind\":\"command_request\",\"command\":\"getAvailabilities\",\"args\":{\"placeId\":1,\"quantity\":1,\"date\":\"" + date + "\"}}");
        assertThatThrownBy(() -> validator.validate(node, "r1")).isInstanceOf(VoiceAiException.class);
    }
}
