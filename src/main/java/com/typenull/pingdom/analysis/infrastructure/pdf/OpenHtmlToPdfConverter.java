package com.typenull.pingdom.analysis.infrastructure.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.typenull.pingdom.analysis.application.pdf.HtmlToPdfConverter;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.File;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OpenHtmlToPdfConverter implements HtmlToPdfConverter {

    private static final String DEFAULT_FONT_PATH = "/usr/share/fonts/truetype/nanum/NanumGothic.ttf";
    private static final List<String> FALLBACK_FONT_PATHS = List.of(
            DEFAULT_FONT_PATH,
            System.getProperty("user.home") + "/Library/Fonts/NanumGothic-Regular.ttf",
            "/usr/share/fonts/truetype/noto/NotoSansKR-Regular.ttf"
    );

    @Value("${analysis.pdf.font-path:" + DEFAULT_FONT_PATH + "}")
    private String fontPath = DEFAULT_FONT_PATH;

    @Override
    public byte[] convert(String html) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(html, null);
            File fontFile = resolveFontFile();
            if (fontFile != null) {
                try {
                    builder.useFont(fontFile, "NanumGothic", 400,
                            com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL, true);
                    builder.useFont(fontFile, "Noto Sans KR", 400,
                            com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL, true);
                } catch (RuntimeException exception) {
                    log.warn("한글 PDF 폰트를 등록하지 못했습니다. path={}", fontFile, exception);
                }
            } else {
                log.warn("한글 PDF 폰트를 찾지 못했습니다. path={}", fontPath);
            }
            builder.toStream(output).run();
            return output.toByteArray();
        } catch (IOException | RuntimeException exception) {
            throw new AnalysisReportException(AnalysisReportErrorCode.PDF_CONVERSION_FAILED, exception);
        }
    }

    private File resolveFontFile() {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(fontPath),
                        FALLBACK_FONT_PATHS.stream()
                )
                .filter(path -> path != null && !path.isBlank())
                .map(File::new)
                .filter(file -> file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".ttf"))
                .filter(File::isFile)
                .findFirst()
                .orElse(null);
    }
}
