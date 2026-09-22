package com.typenull.pingdom.consultation.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import org.junit.jupiter.api.Test;

class ProviderEnvelopeValidatorTest {
    private final ProviderEnvelopeValidator validator = new ProviderEnvelopeValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 버전 1·일치하는 요청 ID·텍스트를 가진 assistant_message를 예외 없이 허용하는지 검증한다.
     */
    @Test
    void acceptsAssistantMessageEnvelope() throws Exception {
        var envelope = objectMapper.readTree("""
                {"schemaVersion":1,"id":"request-1","kind":"assistant_message","text":"무엇을 찾고 계신가요?"}
                """);

        assertThatCode(() -> validator.validate(envelope, "request-1")).doesNotThrowAnyException();
    }

    /**
     * 필수 placeId가 없는 getPlaceDetails 명령은 VoiceAiException으로 거절되는지 검증한다.
     */
    @Test
    void rejectsCommandWithoutRequiredArgs() throws Exception {
        var envelope = objectMapper.readTree("""
                {"schemaVersion":1,"id":"request-1","kind":"command_request","command":"getPlaceDetails","args":{}}
                """);

        assertThatThrownBy(() -> validator.validate(envelope, "request-1"))
                .isInstanceOf(VoiceAiException.class);
    }

    /**
     * 날짜·시간·수량·현재 위치 사용을 갖춘 주변 예약 검색은 선택 category 없이도 허용되는지 검증한다.
     */
    @Test
    void acceptsSearchWithoutOptionalCategory() throws Exception {
        var envelope = objectMapper.readTree("""
                {"schemaVersion":1,"id":"request-1","kind":"command_request","command":"searchNearbyReservablePlaces","args":{"date":"2026-09-20","startTime":"14:00","endTime":"17:00","quantity":2,"useCurrentLocation":true}}
                """);

        assertThatCode(() -> validator.validate(envelope, "request-1")).doesNotThrowAnyException();
    }

    /**
     * clarification_request·protocol_error·세션 취소 명령의 공급 JSON이 요청 ID 계약을 만족하면 통과하는지 검증한다.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "{\"schemaVersion\":1,\"id\":\"r1\",\"kind\":\"clarification_request\",\"field\":\"quantity\",\"text\":\"몇 명인가요?\"}",
        "{\"schemaVersion\":1,\"id\":\"r1\",\"kind\":\"protocol_error\",\"code\":\"INVALID_RESPONSE\"}",
        "{\"schemaVersion\":1,\"id\":\"r1\",\"kind\":\"command_request\",\"command\":\"cancelVoiceSession\",\"args\":{}}"
    })
    void acceptsRemainingEnvelopeKinds(String json) throws Exception {
        assertThatCode(() -> validator.validate(objectMapper.readTree(json), "r1")).doesNotThrowAnyException();
    }

    /**
     * 소수·문자열·boolean·정수 범위를 넘는 schemaVersion을 거절해 숫자 변환으로 버전 1이 오인되는 것을 방지한다.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"1.5", "\"1\"", "true", "4294967297"})
    void rejectsNonV1IntegerVersion(String version) throws Exception {
        var node = objectMapper.readTree("{\"schemaVersion\":" + version + ",\"id\":\"r1\",\"kind\":\"assistant_message\",\"text\":\"ok\"}");
        assertThatThrownBy(() -> validator.validate(node, "r1")).isInstanceOf(VoiceAiException.class);
    }

    /**
     * 소수·0·JavaScript 안전 정수 범위 초과·문자열 placeId가 모두 거절되는지 검증한다.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"1.5", "0", "9007199254740992", "\"1\""})
    void rejectsInvalidResourceId(String id) throws Exception {
        var node = objectMapper.readTree("{\"schemaVersion\":1,\"id\":\"r1\",\"kind\":\"command_request\",\"command\":\"getPlaceDetails\",\"args\":{\"placeId\":" + id + "}}");
        assertThatThrownBy(() -> validator.validate(node, "r1")).isInstanceOf(VoiceAiException.class);
    }

    /**
     * 표현이 1.0이어도 수학적으로 정수인 schemaVersion과 placeId는 허용해 클라이언트 숫자 직렬화 차이를 수용하는지 검증한다.
     */
    @Test
    void acceptsIntegralJsonNumbers() throws Exception {
        var node = objectMapper.readTree("{\"schemaVersion\":1.0,\"id\":\"r1\",\"kind\":\"command_request\",\"command\":\"getPlaceDetails\",\"args\":{\"placeId\":1.0}}");
        assertThatCode(() -> validator.validate(node, "r1")).doesNotThrowAnyException();
    }

    /**
     * 유효한 슬롯 조회·예약 준비 인자를 가진 명령에 공통 envelope 필드를 더하면 검증을 통과하는지 확인한다.
     */
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

    /**
     * 0년·존재하지 않는 윤일·다섯 자리 연도의 예약 조회 날짜를 거절해 앱 날짜 계약을 유지하는지 검증한다.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"0000-01-01", "2026-02-29", "+10000-01-01"})
    void rejectsDatesOutsideAppContract(String date) throws Exception {
        var node = objectMapper.readTree("{\"schemaVersion\":1,\"id\":\"r1\",\"kind\":\"command_request\",\"command\":\"getAvailabilities\",\"args\":{\"placeId\":1,\"quantity\":1,\"date\":\"" + date + "\"}}");
        assertThatThrownBy(() -> validator.validate(node, "r1")).isInstanceOf(VoiceAiException.class);
    }
}
