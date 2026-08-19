package com.typenull.pingdom.analysis.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiAnalysisPropertiesTest {

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
