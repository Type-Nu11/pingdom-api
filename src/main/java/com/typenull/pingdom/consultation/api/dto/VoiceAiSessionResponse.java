package com.typenull.pingdom.consultation.api.dto;

import java.time.LocalDateTime;

public record VoiceAiSessionResponse(String sessionId, LocalDateTime expiresAt) {
}
