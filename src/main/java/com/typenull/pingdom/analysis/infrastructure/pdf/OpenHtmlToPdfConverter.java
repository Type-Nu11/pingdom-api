package com.typenull.pingdom.analysis.infrastructure.pdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.typenull.pingdom.analysis.application.pdf.HtmlToPdfConverter;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class OpenHtmlToPdfConverter implements HtmlToPdfConverter {

    @Override
    public byte[] convert(String html) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(html, null)
                    .toStream(output)
                    .run();
            return output.toByteArray();
        } catch (IOException | RuntimeException exception) {
            throw new AnalysisReportException(AnalysisReportErrorCode.PDF_CONVERSION_FAILED, exception);
        }
    }
}
