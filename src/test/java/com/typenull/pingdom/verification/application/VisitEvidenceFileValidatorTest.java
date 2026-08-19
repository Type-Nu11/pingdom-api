package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.*;

import com.typenull.pingdom.verification.domain.exception.*;
import java.time.Duration;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** 방문 증빙 파일의 형식·크기·픽셀 검증 기준을 검증합니다. */
class VisitEvidenceFileValidatorTest {
    private final VisitEvidenceFileValidator validator = new VisitEvidenceFileValidator(
            new VisitEvidenceProperties(Duration.ofDays(30), 1024L, 10, 10));

    @Test
    void acceptsPngMatchingDeclaredContentType() throws Exception {
        byte[] png = image("png");

        var result = validator.validate(new MockMultipartFile("file", "visit.png", "image/png", png));

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.originalFilename()).isEqualTo("visit.png");
    }

    @Test
    void rejectsContentTypeThatDoesNotMatchSignature() throws Exception {
        byte[] jpeg = image("jpg");

        assertError(new MockMultipartFile("file", "visit.png", "image/png", jpeg),
                VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_INVALID);
    }

    @Test
    void rejectsFileExceedingConfiguredLimit() {
        VisitEvidenceFileValidator smallLimitValidator = new VisitEvidenceFileValidator(
                new VisitEvidenceProperties(Duration.ofDays(30), 10L, 10, 10));
        assertThatThrownBy(() -> smallLimitValidator.validate(
                new MockMultipartFile("file", "visit.png", "image/png", new byte[11])))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(
                VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_TOO_LARGE);
    }

    @Test
    void rejectsPayloadWithOnlyPngSignature() {
        byte[] fakePng = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1};
        assertError(new MockMultipartFile("file", "visit.png", "image/png", fakePng),
                VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_INVALID);
    }

    @Test
    void rejectsOversizedResolutionBeforeDecodingImageBody() throws Exception {
        byte[] png = image("png");
        png[16] = 0;
        png[17] = 0;
        png[18] = 0x1f;
        png[19] = 0x41;

        assertError(new MockMultipartFile("file", "visit.png", "image/png", png),
                VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_INVALID);
    }

    private byte[] image(String format) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output);
        return output.toByteArray();
    }

    private void assertError(MockMultipartFile file, VisitorVerificationErrorCode expected) {
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
