package com.typenull.pingdom.verification.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrectionStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class VisitorVerificationReportCorrectionRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void languageCodeIsTrimmedBeforeCorrectionValidation() {
        VisitorVerificationReportCorrectionRequest request = new VisitorVerificationReportCorrectionRequest(
                "수정 내용", null, null, " en-US ", null, null);

        assertThat(request.languageCode()).isEqualTo("en-US");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void correctionRequestRequiresDescriptionAndHttpsEvidenceUrl() {
        VisitorVerificationReportCorrectionRequest request = new VisitorVerificationReportCorrectionRequest(
                " ", "http://example.com/evidence", null, null, null, null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("description", "evidenceUrl");
    }

    @Test
    void correctionReviewRequestRequiresDecision() {
        VisitorVerificationReportCorrectionReviewRequest request =
                new VisitorVerificationReportCorrectionReviewRequest(null, null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("decision");
        assertThat(VisitorVerificationReportCorrectionStatus.values())
                .contains(VisitorVerificationReportCorrectionStatus.SUBMITTED);
    }
}
