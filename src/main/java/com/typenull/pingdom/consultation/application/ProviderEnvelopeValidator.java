package com.typenull.pingdom.consultation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiErrorCode;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** gateway 경계에서 앱 ProviderEnvelope v1 schema의 허용 union만 통과. */
@Component
@Slf4j
public class ProviderEnvelopeValidator {
    private static final Set<String> COMMON = Set.of("schemaVersion", "id", "kind");
    private static final Set<String> COMMANDS = Set.of(
            "searchNearbyPlaces", "searchNearbyReservablePlaces", "getPlaceDetails", "getAvailabilities",
            "prepareReservation", "cancelVoiceSession"
    );
    private static final Set<String> CLARIFICATION_FIELDS = Set.of(
            "touristCategory", "date", "timeRange", "quantity", "useCurrentLocation", "placeId", "availabilityId"
    );
    private static final Set<String> CATEGORIES = Set.of(
            "K_POP", "BEAUTY", "FASHION", "CAFE", "FOOD", "POP_UP", "EXHIBITION", "NIGHTLIFE", "OTHER"
    );

    /**
     * 공급자 응답의 버전 1·요청 ID 일치·ID 형식을 확인한 뒤 종류별 필드와 명령 인자 검증.
     * 지원하지 않는 종류·필드·값은 PROVIDER_RESPONSE_INVALID로 거절. 명령 실행·자원 접근 인가는 별도 검증 책임.
     */
    public void validate(JsonNode envelope, String requestId) {
        if (envelope == null || !envelope.isObject()) invalid("not_object", "$", envelope);
        if (!integer(envelope.path("schemaVersion")) || !envelope.path("schemaVersion").canConvertToInt()
                || envelope.path("schemaVersion").intValue() != 1) invalid("schema_version", "schemaVersion", envelope.path("schemaVersion"));
        if (!envelope.path("id").isTextual() || !requestId.equals(envelope.path("id").asText())) invalid("request_id_mismatch", "id", envelope.path("id"));
        if (!envelope.path("id").asText().matches("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")) invalid("request_id_format", "id", envelope.path("id"));
        switch (envelope.path("kind").asText()) {
            case "command_request" -> validateCommand(envelope);
            case "clarification_request" -> validateClarification(envelope);
            case "assistant_message" -> validateAssistantMessage(envelope);
            case "protocol_error" -> validateProtocolError(envelope);
            default -> invalid("unsupported_kind", "kind", envelope.path("kind"));
        }
    }

    private void validateCommand(JsonNode node) {
        exactFields(node, Set.of("schemaVersion", "id", "kind", "command", "args"));
        String command = node.path("command").asText();
        JsonNode args = node.path("args");
        if (!COMMANDS.contains(command) || !args.isObject()) invalid("unsupported_command_or_args");
        switch (command) {
            case "searchNearbyPlaces" -> {
                allowedFields(args, Set.of("useCurrentLocation", "touristCategory"));
                required(args, "useCurrentLocation");
                if (!args.path("useCurrentLocation").isBoolean()) invalid("search_arguments");
                if (args.has("touristCategory") && !CATEGORIES.contains(args.path("touristCategory").asText())) invalid("tourist_category");
            }
            case "searchNearbyReservablePlaces" -> {
                allowedFields(args, Set.of("touristCategory", "date", "startTime", "endTime", "quantity", "useCurrentLocation"));
                required(args, "date", "startTime", "endTime", "quantity", "useCurrentLocation");
                if (args.has("touristCategory") && !CATEGORIES.contains(args.path("touristCategory").asText())) invalid("tourist_category");
                validDate(args.path("date").asText());
                LocalTime start = validTime(args.path("startTime").asText());
                LocalTime end = validTime(args.path("endTime").asText());
                if (!start.isBefore(end) || !quantity(args.path("quantity")) || !args.path("useCurrentLocation").isBoolean()) invalid("search_arguments");
            }
            case "getPlaceDetails" -> { exactFields(args, Set.of("placeId")); required(args, "placeId"); positiveId(args.path("placeId")); }
            case "getAvailabilities" -> {
                exactFields(args, Set.of("placeId", "date", "quantity")); required(args, "placeId", "date", "quantity");
                positiveId(args.path("placeId")); validDate(args.path("date").asText()); if (!quantity(args.path("quantity"))) invalid("invalid_argument");
            }
            case "prepareReservation" -> {
                exactFields(args, Set.of("placeId", "availabilityId", "quantity")); required(args, "placeId", "availabilityId", "quantity");
                positiveId(args.path("placeId")); positiveId(args.path("availabilityId")); if (!quantity(args.path("quantity"))) invalid("invalid_argument");
            }
            case "cancelVoiceSession" -> exactFields(args, Set.of());
            default -> invalid("invalid_argument");
        }
    }

    private void validateClarification(JsonNode node) {
        exactFields(node, Set.of("schemaVersion", "id", "kind", "field", "text"));
        required(node, "field", "text");
        if (!CLARIFICATION_FIELDS.contains(node.path("field").asText())) invalid("clarification_field");
        validText(node.path("text"));
    }

    private void validateAssistantMessage(JsonNode node) {
        exactFields(node, Set.of("schemaVersion", "id", "kind", "text"));
        required(node, "text"); validText(node.path("text"));
    }

    private void validateProtocolError(JsonNode node) {
        exactFields(node, Set.of("schemaVersion", "id", "kind", "code"));
        required(node, "code");
        if (!Set.of("UNSUPPORTED_REQUEST", "PROVIDER_UNAVAILABLE", "INVALID_RESPONSE").contains(node.path("code").asText())) invalid("invalid_argument");
    }

    private void exactFields(JsonNode node, Set<String> allowed) {
        if (node.size() != allowed.size()) invalid("field_count");
        allowedFields(node, allowed);
    }

    private void allowedFields(JsonNode node, Set<String> allowed) {
        java.util.Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            if (!allowed.contains(fieldNames.next())) invalid("unexpected_field");
        }
    }

    private void required(JsonNode node, String... fields) { for (String field : fields) if (node.path(field).isMissingNode() || node.path(field).isNull()) invalid("missing_field", field, node.path(field)); }
    private boolean integer(JsonNode node) {
        // JSON Schema와 JavaScript number의 정수 의미에 맞춰 1.0은 허용하되 1.5는 거부.
        return node.isNumber() && node.decimalValue().stripTrailingZeros().scale() <= 0;
    }

    /**
     * 클라이언트의 JavaScript 정수 표현 범위를 넘지 않는 양수 ID만 허용.
     * 실제 DB 존재 여부와 해당 자원에 대한 사용자 권한은 스키마 검증 범위에서 제외.
     */
    private void positiveId(JsonNode node) {
        if (!integer(node) || !node.canConvertToLong() || node.asLong() < 1
                || node.asLong() > 9_007_199_254_740_991L) invalid("resource_id");
    }

    private boolean quantity(JsonNode node) {
        return integer(node) && node.canConvertToInt() && node.asInt() >= 1 && node.asInt() <= 12;
    }

    private void validDate(String value) {
        try {
            if (!value.matches("^(?!0000)[0-9]{4}-[0-9]{2}-[0-9]{2}$")) invalid("date_format");
            LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException exception) {
            invalid("invalid_date_or_time");
        }
    }

    private LocalTime validTime(String value) {
        try {
            if (!value.matches("^(?:[01][0-9]|2[0-3]):[0-5][0-9]$")) invalid("time_format");
            return LocalTime.parse(value, DateTimeFormatter.ISO_LOCAL_TIME);
        } catch (DateTimeParseException exception) {
            invalid("invalid_date_or_time");
            return LocalTime.MIDNIGHT;
        }
    }

    private void validText(JsonNode node) { if (!node.isTextual() || node.asText().isBlank() || node.asText().length() > 2_000) invalid("text_type_or_length", "text", node); }
    private void invalid(String reason) {
        // 값이나 원본 필드명 대신 고정 사유만 기록해 공급자 생성 내용의 로그 유출을 막는다.
        log.warn("Voice AI provider failure provider=gemini outcome=invalid_response reason={}", reason);
        throw new VoiceAiException(VoiceAiErrorCode.PROVIDER_RESPONSE_INVALID);
    }

    private void invalid(String reason, String field, JsonNode value) {
        // field는 코드에서 지정한 계약 필드만 사용하고 공급자가 반환한 이름·값은 기록하지 않는다.
        log.warn("Voice AI provider failure provider=gemini outcome=invalid_response reason={} field={} valueType={}",
                reason, field, value == null ? "NULL" : value.getNodeType());
        throw new VoiceAiException(VoiceAiErrorCode.PROVIDER_RESPONSE_INVALID);
    }
}
