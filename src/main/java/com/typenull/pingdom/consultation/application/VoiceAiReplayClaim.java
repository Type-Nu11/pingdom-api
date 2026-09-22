package com.typenull.pingdom.consultation.application;

import com.fasterxml.jackson.databind.JsonNode;

/** 짧은 DB 트랜잭션이 반환하는 요청 처리 소유권 결과. */
record VoiceAiReplayClaim(Type type, String processingToken, JsonNode envelope) {
    enum Type {
        OWNER,
        PROCESSING,
        COMPLETED
    }

    static VoiceAiReplayClaim owner(String processingToken) {
        return new VoiceAiReplayClaim(Type.OWNER, processingToken, null);
    }

    static VoiceAiReplayClaim processing() {
        return new VoiceAiReplayClaim(Type.PROCESSING, null, null);
    }

    static VoiceAiReplayClaim completed(JsonNode envelope) {
        return new VoiceAiReplayClaim(Type.COMPLETED, null, envelope);
    }
}
