package com.typenull.pingdom.consultation.application;

import com.typenull.pingdom.consultation.api.dto.ConsultationIntroResponse;
import com.typenull.pingdom.consultation.infrastructure.gemini.GeminiProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 상담 입력을 외부 생성 클라이언트에 전달하고 응답 형식으로 변환합니다. */
@Service
public class ConsultationIntroService {

    static final String FALLBACK_MESSAGE = "어떤 업종을 준비하고 계신가요? 카테고리를 선택해 주세요.";

    private final GeminiProperties geminiProperties;
    private final GeminiIntroClient geminiIntroClient;

    public ConsultationIntroService(GeminiProperties geminiProperties, GeminiIntroClient geminiIntroClient) {
        this.geminiProperties = geminiProperties;
        this.geminiIntroClient = geminiIntroClient;
    }

    /**
     * Gemini 비활성·키 누락·빈 응답·호출 예외는 고정 안내와 source=fallback으로 대체합니다.
     * 외부 응답의 길이나 의미는 여기서 재검증하지 않으며 저장 및 재시도를 수행하지 않습니다.
     */
    public ConsultationIntroResponse createIntro(String message) {
        if (!geminiProperties.enabled() || !StringUtils.hasText(geminiProperties.apiKey())) {
            return fallback();
        }

        try {
            return geminiIntroClient.generateIntro(message)
                    .filter(StringUtils::hasText)
                    .map(response -> new ConsultationIntroResponse(response.trim(), "gemini"))
                    .orElseGet(this::fallback);
        } catch (RuntimeException exception) {
            return fallback();
        }
    }

    private ConsultationIntroResponse fallback() {
        return new ConsultationIntroResponse(FALLBACK_MESSAGE, "fallback");
    }
}
