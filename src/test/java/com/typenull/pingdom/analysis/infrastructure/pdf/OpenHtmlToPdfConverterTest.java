package com.typenull.pingdom.analysis.infrastructure.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class OpenHtmlToPdfConverterTest {

    private final OpenHtmlToPdfConverter converter = new OpenHtmlToPdfConverter();

    @Test
    void convertsHtmlToPdfBytes() {
        byte[] pdf = converter.convert("<html><body><h1>한글 입지 분석 보고서</h1></body></html>");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(new ClassPathResource("fonts/NanumGothic.ttf").exists()).isTrue();
    }
}
