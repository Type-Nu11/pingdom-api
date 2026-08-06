package com.typenull.pingdom.verification.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class ScoutProfileRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidProfileDetails() {
        ScoutProfileRequest request = new ScoutProfileRequest("서울 현장 Scout", "관광객에게 최신 장소 정보를 전달합니다.");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsBlankDisplayName() {
        ScoutProfileRequest request = new ScoutProfileRequest("   ", "소개");

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("displayName"));
    }

    @Test
    void acceptsMaximumLengthDisplayNameAndIntroduction() {
        ScoutProfileRequest request = new ScoutProfileRequest("a".repeat(100), "b".repeat(1000));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsTextThatExceedsConfiguredLimits() {
        ScoutProfileRequest request = new ScoutProfileRequest("a".repeat(101), "b".repeat(1001));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("displayName", "introduction");
    }
}
