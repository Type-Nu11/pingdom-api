package com.typenull.pingdom.verification.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.verification.domain.CrowdLevel;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class VisitorVerificationReportCreateRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /** 생성 요청의 언어 태그 공백을 제거한 en-US 값과 validation 성공을 확인. */
    @Test
    void trimReportLanguageCode() {
        VisitorVerificationReportCreateRequest request = new VisitorVerificationReportCreateRequest(
                2L, VisitorVerificationReportType.LANGUAGE_SUPPORT, "영어 지원", null,
                null, " en-US ", null, null);

        assertThat(request.languageCode()).isEqualTo("en-US");
        assertThat(validator.validate(request)).isEmpty();
    }

    /** english 언어 태그와 1,441분 대기 시간은 각각 대응 필드 위반으로 검출되어야 함. */
    @Test
    void rejectInvalidStructuredInputs() {
        VisitorVerificationReportCreateRequest invalidLanguage = new VisitorVerificationReportCreateRequest(
                2L, VisitorVerificationReportType.LANGUAGE_SUPPORT, "언어 지원", null,
                null, "english", null, null);
        VisitorVerificationReportCreateRequest invalidWait = new VisitorVerificationReportCreateRequest(
                2L, VisitorVerificationReportType.WAIT_TIME, "대기 시간", null,
                1441, null, null, null);

        assertThat(validator.validate(invalidLanguage)).anyMatch(
                violation -> violation.getPropertyPath().toString().equals("languageCode"));
        assertThat(validator.validate(invalidWait)).anyMatch(
                violation -> violation.getPropertyPath().toString().equals("waitTimeMinutes"));
    }

    /**
     * 혼잡도 FULL enum을 요청 객체가 유지하고 validation을 통과하는지 확인.
     * JSON 직렬화 실행은 검증 범위에서 제외.
     */
    @Test
    void retainCrowdLevelEnum() {
        VisitorVerificationReportCreateRequest request = new VisitorVerificationReportCreateRequest(
                2L, VisitorVerificationReportType.CROWD_LEVEL, "매우 혼잡", null,
                null, null, null, CrowdLevel.FULL);

        assertThat(request.crowdLevel()).isEqualTo(CrowdLevel.FULL);
        assertThat(validator.validate(request)).isEmpty();
    }
}
