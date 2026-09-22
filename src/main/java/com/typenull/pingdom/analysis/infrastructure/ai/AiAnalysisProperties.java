package com.typenull.pingdom.analysis.infrastructure.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * AI 공급자·주소·모델·인증키와 HTTP 제한 시간을 바인딩합니다.
 * null 제한 시간은 연결 2초·응답 2분으로 보충하지만 0이나 음수 값을 여기서 검증하지는 않습니다.
 * 레코드의 기본 공급자와 실제 Bean 선택 조건은 별개이므로 설정 누락 시 선택은 구성 클래스에 따릅니다.
 */
@ConfigurationProperties(prefix = "analysis.ai")
public record AiAnalysisProperties(
        String provider,
        String baseUrl,
        String model,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout
) {

    @ConstructorBinding
    public AiAnalysisProperties {
        provider = defaultValue(provider, "gemini");
        baseUrl = defaultValue(baseUrl, defaultBaseUrl(provider));
        model = defaultValue(model, defaultModel(provider));
        apiKey = apiKey == null ? "" : apiKey;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofMinutes(2) : readTimeout;
    }

    public AiAnalysisProperties(
            String provider,
            String baseUrl,
            String model,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        this(provider, baseUrl, model, "", connectTimeout, readTimeout);
    }

    private static String defaultBaseUrl(String provider) {
        return "gemini".equalsIgnoreCase(provider)
                ? "https://generativelanguage.googleapis.com/v1beta"
                : "http://localhost:11434";
    }

    private static String defaultModel(String provider) {
        return "gemini".equalsIgnoreCase(provider) ? "gemini-3.1-flash-lite" : "qwen2.5:7b";
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
