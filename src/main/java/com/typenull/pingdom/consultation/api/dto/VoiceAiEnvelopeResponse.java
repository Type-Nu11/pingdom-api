package com.typenull.pingdom.consultation.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** 앱의 parseVoiceAssistantEnvelope(unknown)에 그대로 전달하는 provider 최종 envelope. */
public record VoiceAiEnvelopeResponse(JsonNode envelope) {
}
