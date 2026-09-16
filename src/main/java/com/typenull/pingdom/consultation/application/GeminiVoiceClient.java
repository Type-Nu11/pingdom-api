package com.typenull.pingdom.consultation.application;

import com.fasterxml.jackson.databind.JsonNode;

public interface GeminiVoiceClient {
    JsonNode generateEnvelope(String message, String requestId);
}
