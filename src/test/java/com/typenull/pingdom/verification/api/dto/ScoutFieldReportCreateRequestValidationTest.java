package com.typenull.pingdom.verification.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.verification.domain.ScoutFieldReportType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class ScoutFieldReportCreateRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /** 필수 제보 정보와 HTTPS 증빙 URL을 주면 Bean Validation 위반이 없어야 한다. */
    @Test
    void acceptHttpsEvidence() {
        ScoutFieldReportCreateRequest request = new ScoutFieldReportCreateRequest(
                2L,
                ScoutFieldReportType.PLACE_INFORMATION,
                "현장 정보 확인",
                "https://example.com/evidence.jpg"
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    /** HTTP 증빙 URL을 주면 evidenceUrl 필드에 검증 위반이 발생해야 한다. */
    @Test
    void rejectHttpEvidence() {
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
