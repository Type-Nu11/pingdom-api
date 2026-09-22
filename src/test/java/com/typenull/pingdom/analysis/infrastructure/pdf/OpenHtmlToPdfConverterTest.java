package com.typenull.pingdom.analysis.infrastructure.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class OpenHtmlToPdfConverterTest {

    private final OpenHtmlToPdfConverter converter = new OpenHtmlToPdfConverter();

    /**
     * 한글 제목 HTML을 실제 변환해 비어 있지 않은 %PDF- 바이트와 번들 NanumGothic 리소스를 확인.
     * 페이지의 시각적 배치와 한글 렌더링 품질은 직접 검증 대상에서 제외.
     */
    @Test
    void convertsHtmlToPdfBytes() {
        byte[] pdf = converter.convert("<html><body><h1>한글 입지 분석 보고서</h1></body></html>");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(new ClassPathResource("fonts/NanumGothic.ttf").exists()).isTrue();
    }
}
