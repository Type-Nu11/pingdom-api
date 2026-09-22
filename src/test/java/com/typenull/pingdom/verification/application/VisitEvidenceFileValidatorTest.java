package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.*;

import com.typenull.pingdom.verification.domain.exception.*;
import java.time.Duration;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** 방문 증빙 파일의 형식·크기·픽셀 검증 기준을 검증. */
class VisitEvidenceFileValidatorTest {
    private final VisitEvidenceFileValidator validator = new VisitEvidenceFileValidator(
            new VisitEvidenceProperties(Duration.ofDays(30), 1024L, 10, 10));

    /**
     * 실제 PNG 바이트와 선언한 image/png가 일치하면 검증을 통과해야 함.
     * 반환된 MIME 타입과 원본 파일명이 유지되는지 확인.
     */
    @Test
    void acceptMatchingPng() throws Exception {
        byte[] png = image("png");

        var result = validator.validate(new MockMultipartFile("file", "visit.png", "image/png", png));

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.originalFilename()).isEqualTo("visit.png");
    }

    /** JPEG 바이트를 PNG라고 선언하면 파일 확장자와 무관하게 잘못된 증빙 오류로 거부. */
    @Test
    void rejectMismatchedMime() throws Exception {
        byte[] jpeg = image("jpg");

        assertError(new MockMultipartFile("file", "visit.png", "image/png", jpeg),
                VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_INVALID);
    }

    /**
     * 최대 10바이트 설정에 11바이트를 전달하면 크기 초과 오류가 발생해야 함.
     * 이미지 판독 전 요청 크기를 제한하는 경계를 확인.
     */
    @Test
    void rejectOversizedFile() {
        VisitEvidenceFileValidator smallLimitValidator = new VisitEvidenceFileValidator(
                new VisitEvidenceProperties(Duration.ofDays(30), 10L, 10, 10));
        assertThatThrownBy(() -> smallLimitValidator.validate(
                new MockMultipartFile("file", "visit.png", "image/png", new byte[11])))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(
                VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_TOO_LARGE);
    }

    /**
     * PNG 시그니처만 있고 유효한 이미지 본문이 없는 입력을 거부.
     * 매직 바이트가 맞다는 이유만으로 손상된 이미지를 허용하는 회귀를 방지.
     */
    @Test
    void rejectIncompletePng() {
        byte[] fakePng = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1};
        assertError(new MockMultipartFile("file", "visit.png", "image/png", fakePng),
                VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_INVALID);
    }

    /**
     * 정상 PNG의 너비 헤더를 8,001로 바꾼 입력이 파일 형식 오류로 거부되는지 확인.
     * 너비 제한 8,000을 넘는 입력을 검증하며 디코더 내부 호출 순서는 검증 범위에서 제외.
     */
    @Test
    void rejectOversizedResolution() throws Exception {
        byte[] png = image("png");
        png[16] = 0;
        png[17] = 0;
        png[18] = 0x1f;
        png[19] = 0x41;

        assertError(new MockMultipartFile("file", "visit.png", "image/png", png),
                VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_INVALID);
    }

    /** 지정한 포맷으로 2×2 RGB 이미지를 메모리에서 인코딩해 정상 이미지 바이트를 제공. */
    private byte[] image(String format) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output);
        return output.toByteArray();
    }

    /** 파일 검증이 방문 인증 예외로 실패하는지와 그 오류 코드가 기대값인지 함께 확인. */
    private void assertError(MockMultipartFile file, VisitorVerificationErrorCode expected) {
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
