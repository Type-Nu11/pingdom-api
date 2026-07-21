package com.typenull.pingdom.verification.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.verification.domain.CrowdLevel;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class VisitorVerificationReportCreateRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void languageCodeIsTrimmedBeforeApiValidation() {
        VisitorVerificationReportCreateRequest request = new VisitorVerificationReportCreateRequest(
                2L, VisitorVerificationReportType.LANGUAGE_SUPPORT, "영어 지원", null,
                null, " en-US ", null, null);

        assertThat(request.languageCode()).isEqualTo("en-US");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void apiValidationRejectsInvalidLanguageAndWaitTime() {
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

    @Test
    void crowdLevelRequestKeepsStructuredEnumForSerializationContract() {
        VisitorVerificationReportCreateRequest request = new VisitorVerificationReportCreateRequest(
                2L, VisitorVerificationReportType.CROWD_LEVEL, "매우 혼잡", null,
                null, null, null, CrowdLevel.FULL);

        assertThat(request.crowdLevel()).isEqualTo(CrowdLevel.FULL);
        assertThat(validator.validate(request)).isEmpty();
    }
}
