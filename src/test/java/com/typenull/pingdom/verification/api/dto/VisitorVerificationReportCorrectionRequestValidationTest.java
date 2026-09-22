package com.typenull.pingdom.verification.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrectionStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class VisitorVerificationReportCorrectionRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /** 정정 언어 태그의 공백을 먼저 제거해 en-US로 보관하고 validation을 통과해야 한다. */
    @Test
    void trimCorrectionLanguageCode() {
        VisitorVerificationReportCorrectionRequest request = new VisitorVerificationReportCorrectionRequest(
                "수정 내용", null, null, " en-US ", null, null);

        assertThat(request.languageCode()).isEqualTo("en-US");
        assertThat(validator.validate(request)).isEmpty();
    }

    /** 공백 본문과 HTTP 증빙 URL은 description·evidenceUrl 두 필드 위반을 발생시킨다. */
    @Test
    void rejectInvalidCorrectionFields() {
        VisitorVerificationReportCorrectionRequest request = new VisitorVerificationReportCorrectionRequest(
                " ", "http://example.com/evidence", null, null, null, null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("description", "evidenceUrl");
    }

    /**
     * 심사 결정이 null이면 decision 필드만 위반해야 한다.
     * 도메인 상태 enum에는 미심사 상태 SUBMITTED가 존재함도 확인한다.
     */
    @Test
    void requireCorrectionDecision() {
        VisitorVerificationReportCorrectionReviewRequest request =
                new VisitorVerificationReportCorrectionReviewRequest(null, null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("decision");
        assertThat(VisitorVerificationReportCorrectionStatus.values())
                .contains(VisitorVerificationReportCorrectionStatus.SUBMITTED);
    }
}
