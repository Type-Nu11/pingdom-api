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

    /**
     * Gemini가 안내 문구를 반환하면 같은 문구와 source=gemini를 응답하는지 검증한다.
     */
    @Test
    void returnsGeminiIntroText() {
        ConsultationIntroService service = service(true, "test-key");
        given(geminiIntroClient.generateIntro("카페를 열고 싶어요"))
                .willReturn(Optional.of("카페 창업을 고민하고 계시는군요. 업종을 선택해 주세요."));

        ConsultationIntroResponse response = service.createIntro("카페를 열고 싶어요");

        assertThat(response).isEqualTo(new ConsultationIntroResponse(
                "카페 창업을 고민하고 계시는군요. 업종을 선택해 주세요.", "gemini"));
    }

    /**
     * Gemini가 비활성이면 고정 안내·fallback 출처를 반환하고 클라이언트를 호출하지 않는지 검증한다.
     */
    @Test
    void disabledGeminiUsesFallback() {
        ConsultationIntroService service = service(false, null);

        ConsultationIntroResponse response = service.createIntro("카페를 열고 싶어요");

        assertThat(response).isEqualTo(new ConsultationIntroResponse(
                ConsultationIntroService.FALLBACK_MESSAGE, "fallback"));
        verify(geminiIntroClient, never()).generateIntro("카페를 열고 싶어요");
    }

    /**
     * Gemini의 빈 결과와 예외 모두 fallback 출처로 처리해 상담 시작이 실패하지 않는지 검증한다.
     */
    @Test
    void fallsBackForUnavailableIntro() {
        ConsultationIntroService service = service(true, "test-key");
        given(geminiIntroClient.generateIntro("빈 응답"))
                .willReturn(Optional.empty());
        given(geminiIntroClient.generateIntro("실패 응답"))
                .willThrow(new IllegalStateException("provider unavailable"));

        assertThat(service.createIntro("빈 응답").source()).isEqualTo("fallback");
        assertThat(service.createIntro("실패 응답").source()).isEqualTo("fallback");
    }

    /**
     * 활성 플래그·API 키와 짧은 시간 제한을 지정해 안내 생성 분기를 검증할 서비스를 구성한다.
     */
    private ConsultationIntroService service(boolean enabled, String apiKey) {
        return new ConsultationIntroService(
                new GeminiProperties(enabled, apiKey, null, Duration.ofSeconds(2), Duration.ofSeconds(5)),
                geminiIntroClient
        );
    }
}
