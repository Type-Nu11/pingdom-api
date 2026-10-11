package com.typenull.pingdom.consultation.application;

import com.typenull.pingdom.consultation.api.dto.AiChatRequest;
import com.typenull.pingdom.consultation.api.dto.AiChatResponse;

public interface GeminiChatClient {
    AiChatResponse chat(AiChatRequest request);
}
