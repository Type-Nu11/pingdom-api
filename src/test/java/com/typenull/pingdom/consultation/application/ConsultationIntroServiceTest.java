package com.typenull.pingdom.consultation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.typenull.pingdom.consultation.api.dto.ConsultationIntroResponse;
import com.typenull.pingdom.consultation.infrastructure.gemini.GeminiProperties;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsultationIntroServiceTest {

    @Mock
    private GeminiIntroClient geminiIntroClient;

    @Test
    void returnsGeminiResponseWhenClientReturnsText() {
        ConsultationIntroService service = service(true, "test-key");
        given(geminiIntroClient.generateIntro("카페를 열고 싶어요"))
                .willReturn(Optional.of("카페 창업을 고민하고 계시는군요. 업종을 선택해 주세요."));

        ConsultationIntroResponse response = service.createIntro("카페를 열고 싶어요");

        assertThat(response).isEqualTo(new ConsultationIntroResponse(
                "카페 창업을 고민하고 계시는군요. 업종을 선택해 주세요.", "gemini"));
    }

    @Test
    void returnsFallbackWithoutCallingClientWhenDisabled() {
        ConsultationIntroService service = service(false, null);

        ConsultationIntroResponse response = service.createIntro("카페를 열고 싶어요");

        assertThat(response).isEqualTo(new ConsultationIntroResponse(
                ConsultationIntroService.FALLBACK_MESSAGE, "fallback"));
        verify(geminiIntroClient, never()).generateIntro("카페를 열고 싶어요");
    }

    @Test
    void returnsFallbackForEmptyOrFailedGeminiResponse() {
        ConsultationIntroService service = service(true, "test-key");
        given(geminiIntroClient.generateIntro("빈 응답"))
                .willReturn(Optional.empty());
        given(geminiIntroClient.generateIntro("실패 응답"))
                .willThrow(new IllegalStateException("provider unavailable"));

        assertThat(service.createIntro("빈 응답").source()).isEqualTo("fallback");
        assertThat(service.createIntro("실패 응답").source()).isEqualTo("fallback");
    }

    private ConsultationIntroService service(boolean enabled, String apiKey) {
        return new ConsultationIntroService(
                new GeminiProperties(enabled, apiKey, null, Duration.ofSeconds(2), Duration.ofSeconds(5)),
                geminiIntroClient
        );
    }
}
