package com.typenull.pingdom.verification.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.verification.domain.ScoutFieldReportType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class ScoutFieldReportCreateRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validHttpsEvidenceUrlIsAccepted() {
        ScoutFieldReportCreateRequest request = new ScoutFieldReportCreateRequest(
                2L,
                ScoutFieldReportType.PLACE_INFORMATION,
                "현장 정보 확인",
                "https://example.com/evidence.jpg"
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void nonHttpsEvidenceUrlIsRejected() {
        ScoutFieldReportCreateRequest request = new ScoutFieldReportCreateRequest(
                2L,
                ScoutFieldReportType.PLACE_INFORMATION,
                "현장 정보 확인",
                "http://example.com/evidence.jpg"
        );

        assertThat(validator.validate(request)).anyMatch(
                violation -> violation.getPropertyPath().toString().equals("evidenceUrl")
        );
    }
}
