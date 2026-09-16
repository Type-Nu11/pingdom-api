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
}
