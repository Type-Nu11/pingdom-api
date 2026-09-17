package com.typenull.pingdom.consultation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiErrorCode;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 앱 ProviderEnvelope v1 schema의 허용 union만 gateway 경계에서 통과시킨다. */
@Component
public class ProviderEnvelopeValidator {
    private static final Set<String> COMMON = Set.of("schemaVersion", "id", "kind");
    private static final Set<String> COMMANDS = Set.of(
            "searchNearbyReservablePlaces", "getPlaceDetails", "getAvailabilities",
            "prepareReservation", "cancelVoiceSession"
    );
    private static final Set<String> CLARIFICATION_FIELDS = Set.of(
            "touristCategory", "date", "timeRange", "quantity", "useCurrentLocation", "placeId", "availabilityId"
    );
    private static final Set<String> CATEGORIES = Set.of(
            "K_POP", "BEAUTY", "FASHION", "CAFE", "FOOD", "POP_UP", "EXHIBITION", "NIGHTLIFE", "OTHER"
    );

    public void validate(JsonNode envelope, String requestId) {
        if (envelope == null || !envelope.isObject()
                || !integer(envelope.path("schemaVersion"))
                || !envelope.path("schemaVersion").canConvertToInt()
                || envelope.path("schemaVersion").intValue() != 1
                || !envelope.path("id").isTextual()
                || !requestId.equals(envelope.path("id").asText())
                || !envelope.path("id").asText().matches("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")) {
            invalid();
        }
        switch (envelope.path("kind").asText()) {
            case "command_request" -> validateCommand(envelope);
            case "clarification_request" -> validateClarification(envelope);
            case "assistant_message" -> validateAssistantMessage(envelope);
            case "protocol_error" -> validateProtocolError(envelope);
            default -> invalid();
        }
    }

    private void validateCommand(JsonNode node) {
        exactFields(node, Set.of("schemaVersion", "id", "kind", "command", "args"));
        String command = node.path("command").asText();
        JsonNode args = node.path("args");
        if (!COMMANDS.contains(command) || !args.isObject()) invalid();
        switch (command) {
            case "searchNearbyReservablePlaces" -> {
                allowedFields(args, Set.of("touristCategory", "date", "startTime", "endTime", "quantity", "useCurrentLocation"));
                required(args, "date", "startTime", "endTime", "quantity", "useCurrentLocation");
                if (args.has("touristCategory") && !CATEGORIES.contains(args.path("touristCategory").asText())) invalid();
                validDate(args.path("date").asText());
                LocalTime start = validTime(args.path("startTime").asText());
                LocalTime end = validTime(args.path("endTime").asText());
                if (!start.isBefore(end) || !quantity(args.path("quantity")) || !args.path("useCurrentLocation").isBoolean()) invalid();
            }
            case "getPlaceDetails" -> { exactFields(args, Set.of("placeId")); required(args, "placeId"); positiveId(args.path("placeId")); }
            case "getAvailabilities" -> {
                exactFields(args, Set.of("placeId", "date", "quantity")); required(args, "placeId", "date", "quantity");
                positiveId(args.path("placeId")); validDate(args.path("date").asText()); if (!quantity(args.path("quantity"))) invalid();
            }
            case "prepareReservation" -> {
                exactFields(args, Set.of("placeId", "availabilityId", "quantity")); required(args, "placeId", "availabilityId", "quantity");
                positiveId(args.path("placeId")); positiveId(args.path("availabilityId")); if (!quantity(args.path("quantity"))) invalid();
            }
            case "cancelVoiceSession" -> exactFields(args, Set.of());
            default -> invalid();
        }
    }

    private void validateClarification(JsonNode node) {
        exactFields(node, Set.of("schemaVersion", "id", "kind", "field", "text"));
        required(node, "field", "text");
        if (!CLARIFICATION_FIELDS.contains(node.path("field").asText())) invalid();
        validText(node.path("text"));
    }

    private void validateAssistantMessage(JsonNode node) {
        exactFields(node, Set.of("schemaVersion", "id", "kind", "text"));
        required(node, "text"); validText(node.path("text"));
    }

    private void validateProtocolError(JsonNode node) {
        exactFields(node, Set.of("schemaVersion", "id", "kind", "code"));
        required(node, "code");
        if (!Set.of("UNSUPPORTED_REQUEST", "PROVIDER_UNAVAILABLE", "INVALID_RESPONSE").contains(node.path("code").asText())) invalid();
    }

    private void exactFields(JsonNode node, Set<String> allowed) {
        if (node.size() != allowed.size()) invalid();
        allowedFields(node, allowed);
    }

    private void allowedFields(JsonNode node, Set<String> allowed) {
        java.util.Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            if (!allowed.contains(fieldNames.next())) invalid();
        }
    }

    private void required(JsonNode node, String... fields) { for (String field : fields) if (node.path(field).isMissingNode() || node.path(field).isNull()) invalid(); }
    private boolean integer(JsonNode node) {
        // JSON Schema와 JavaScript number의 정수 의미에 맞춰 1.0은 허용하되 1.5는 거부한다.
        return node.isNumber() && node.decimalValue().stripTrailingZeros().scale() <= 0;
    }

    private void positiveId(JsonNode node) {
        if (!integer(node) || !node.canConvertToLong() || node.asLong() < 1
                || node.asLong() > 9_007_199_254_740_991L) invalid();
    }

    private boolean quantity(JsonNode node) {
        return integer(node) && node.canConvertToInt() && node.asInt() >= 1 && node.asInt() <= 12;
    }

    private void validDate(String value) {
        try {
            if (!value.matches("^(?!0000)[0-9]{4}-[0-9]{2}-[0-9]{2}$")) invalid();
            LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException exception) {
            invalid();
        }
    }

    private LocalTime validTime(String value) {
        try {
            if (!value.matches("^(?:[01][0-9]|2[0-3]):[0-5][0-9]$")) invalid();
            return LocalTime.parse(value, DateTimeFormatter.ISO_LOCAL_TIME);
        } catch (DateTimeParseException exception) {
            invalid();
            return LocalTime.MIDNIGHT;
        }
    }

    private void validText(JsonNode node) { if (!node.isTextual() || node.asText().isBlank() || node.asText().length() > 2_000) invalid(); }
    private void invalid() { throw new VoiceAiException(VoiceAiErrorCode.PROVIDER_RESPONSE_INVALID); }
}
