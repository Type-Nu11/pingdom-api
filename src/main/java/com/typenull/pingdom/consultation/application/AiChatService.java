package com.typenull.pingdom.consultation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.consultation.api.dto.AiChatRequest;
import com.typenull.pingdom.consultation.api.dto.AiChatResponse;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiErrorCode;
import com.typenull.pingdom.consultation.domain.exception.VoiceAiException;
import com.typenull.pingdom.consultation.infrastructure.gemini.GeminiProperties;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiChatService {
    private static final Pattern TOOL_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_-]{0,63}$");

    private final GeminiProperties properties;
    private final GeminiChatClient client;

    public AiChatService(GeminiProperties properties, GeminiChatClient client) {
        this.properties = properties;
        this.client = client;
    }

    public AiChatResponse chat(AiChatRequest request) {
        Map<String, JsonNode> schemas = validateRequest(request);
        if (!properties.enabled() || !StringUtils.hasText(properties.apiKey())) {
            throw new VoiceAiException(VoiceAiErrorCode.PROVIDER_UNAVAILABLE);
        }
        try {
            AiChatResponse response = client.chat(request);
            if (response == null || (!StringUtils.hasText(response.content()) && response.toolCalls().isEmpty())) {
                throw new VoiceAiException(VoiceAiErrorCode.PROVIDER_RESPONSE_INVALID);
            }
            for (AiChatRequest.ToolCall call : response.toolCalls()) {
                validateToolCall(call, schemas, VoiceAiErrorCode.PROVIDER_RESPONSE_INVALID);
            }
            return response;
        } catch (VoiceAiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new VoiceAiException(VoiceAiErrorCode.PROVIDER_UNAVAILABLE, exception);
        }
    }

    private Map<String, JsonNode> validateRequest(AiChatRequest request) {
        Map<String, JsonNode> schemas = new HashMap<>();
        for (AiChatRequest.Tool tool : request.tools()) {
            if (tool.function() == null || !"function".equals(tool.type())
                    || !TOOL_NAME.matcher(tool.function().name()).matches()
                    || !validRootSchema(tool.function().parameters())
                    || schemas.putIfAbsent(tool.function().name(), tool.function().parameters()) != null) {
                invalidRequest();
            }
        }

        Set<String> pendingCallIds = new HashSet<>();
        for (AiChatRequest.Message message : request.messages()) {
            switch (message.role()) {
                case "user" -> {
                    if (!textContent(message.content()) || StringUtils.hasText(message.toolCallId())
                            || !message.toolCalls().isEmpty()) invalidRequest();
                }
                case "assistant" -> {
                    if (message.content() != null && !message.content().isNull() && !textContent(message.content())) invalidRequest();
                    if (!StringUtils.hasText(text(message.content())) && message.toolCalls().isEmpty()) invalidRequest();
                    for (AiChatRequest.ToolCall call : message.toolCalls()) {
                        validateToolCall(call, schemas, VoiceAiErrorCode.TOOL_CALL_INVALID);
                        if (!pendingCallIds.add(call.id())) invalidRequest();
                    }
                }
                case "tool" -> {
                    if (!StringUtils.hasText(message.toolCallId()) || message.content() == null
                            || message.content().isNull() || !message.toolCalls().isEmpty()
                            || !pendingCallIds.remove(message.toolCallId())) invalidRequest();
                }
                default -> invalidRequest();
            }
        }
        String lastRole = request.messages().get(request.messages().size() - 1).role();
        if (!Set.of("user", "tool").contains(lastRole)) invalidRequest();
        return schemas;
    }

    private void validateToolCall(AiChatRequest.ToolCall call, Map<String, JsonNode> schemas,
                                  VoiceAiErrorCode errorCode) {
        if (call == null || !StringUtils.hasText(call.id()) || !"function".equals(call.type())
                || call.function() == null || !schemas.containsKey(call.function().name())
                || !validArguments(call.function().arguments(), schemas.get(call.function().name()))) {
            throw new VoiceAiException(errorCode);
        }
    }

    private boolean validRootSchema(JsonNode schema) {
        return schema != null && schema.isObject() && "object".equals(schema.path("type").asText())
                && schema.path("properties").isObject()
                && (!schema.has("required") || schema.path("required").isArray());
    }

    private boolean validArguments(JsonNode arguments, JsonNode schema) {
        if (arguments == null || !arguments.isObject()) return false;
        Set<String> properties = new HashSet<>();
        schema.path("properties").fieldNames().forEachRemaining(properties::add);
        if (!schema.path("additionalProperties").asBoolean(true)
                && !java.util.stream.StreamSupport.stream(
                java.util.Spliterators.spliteratorUnknownSize(arguments.fieldNames(), 0), false)
                .allMatch(properties::contains)) return false;
        for (JsonNode required : schema.path("required")) {
            if (!arguments.has(required.asText())) return false;
        }
        for (String property : properties) {
            if (arguments.has(property) && !validValue(arguments.get(property), schema.path("properties").path(property))) {
                return false;
            }
        }
        return true;
    }

    private boolean validValue(JsonNode value, JsonNode schema) {
        if (schema.has("enum") && !java.util.stream.StreamSupport.stream(schema.path("enum").spliterator(), false)
                .anyMatch(value::equals)) return false;
        String type = schema.path("type").asText();
        if ("string".equals(type)) {
            int length = value.isTextual() ? value.textValue().codePointCount(0, value.textValue().length()) : -1;
            return length >= schema.path("minLength").asInt(0)
                    && (!schema.has("maxLength") || length <= schema.path("maxLength").asInt());
        }
        if ("integer".equals(type)) {
            if (!value.isIntegralNumber()) return false;
            BigDecimal number = value.decimalValue();
            return (!schema.has("minimum") || number.compareTo(schema.path("minimum").decimalValue()) >= 0)
                    && (!schema.has("maximum") || number.compareTo(schema.path("maximum").decimalValue()) <= 0);
        }
        if ("number".equals(type)) return value.isNumber();
        if ("boolean".equals(type)) return value.isBoolean();
        if ("object".equals(type)) return value.isObject() && validArguments(value, schema);
        if ("array".equals(type)) return value.isArray() && (!schema.has("items")
                || java.util.stream.StreamSupport.stream(value.spliterator(), false)
                .allMatch(item -> validValue(item, schema.path("items"))));
        return false;
    }

    private boolean textContent(JsonNode content) {
        return content != null && content.isTextual() && StringUtils.hasText(content.textValue())
                && content.textValue().length() <= 8_000;
    }

    private String text(JsonNode content) {
        return content != null && content.isTextual() ? content.textValue() : null;
    }

    private void invalidRequest() {
        throw new VoiceAiException(VoiceAiErrorCode.CHAT_REQUEST_INVALID);
    }

}
