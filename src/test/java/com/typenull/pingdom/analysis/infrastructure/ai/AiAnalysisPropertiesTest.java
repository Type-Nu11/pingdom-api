package com.typenull.pingdom.analysis.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiAnalysisPropertiesTest {

    /**
     * 설정을 모두 생략하면 공급자 gemini·공식 v1beta 주소·gemini-3.1-flash-lite 모델을 기본값으로 선택하는지 검증한다.
     */
    @Test
    void defaultsToGeminiFlashLite() {
        AiAnalysisProperties properties = new AiAnalysisProperties(
                null, null, null, null, null, null
        );

        assertThat(properties.provider()).isEqualTo("gemini");
        assertThat(properties.baseUrl()).isEqualTo("https://generativelanguage.googleapis.com/v1beta");
        assertThat(properties.model()).isEqualTo("gemini-3.1-flash-lite");
    }
}
