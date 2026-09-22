package com.typenull.pingdom.verification.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class ScoutProfileRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /** 유효한 표시 이름과 소개에는 Bean Validation 오류가 없어야 함. */
    @Test
    void acceptsValidProfileDetails() {
        ScoutProfileRequest request = new ScoutProfileRequest("서울 현장 Scout", "관광객에게 최신 장소 정보를 전달합니다.");

        assertThat(validator.validate(request)).isEmpty();
    }

    /** 표시 이름이 공백뿐이면 displayName 필드 위반을 반환. */
    @Test
    void rejectsBlankDisplayName() {
        ScoutProfileRequest request = new ScoutProfileRequest("   ", "소개");

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("displayName"));
    }

    /** 표시 이름 100자와 소개 1,000자는 최대 길이 경계로 허용. */
    @Test
    void acceptMaximumTextLengths() {
        ScoutProfileRequest request = new ScoutProfileRequest("a".repeat(100), "b".repeat(1000));

        assertThat(validator.validate(request)).isEmpty();
    }

    /** 표시 이름 101자와 소개 1,001자는 두 필드 모두 길이 위반으로 반환. */
    @Test
    void rejectOversizedText() {
        ScoutProfileRequest request = new ScoutProfileRequest("a".repeat(101), "b".repeat(1001));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("displayName", "introduction");
    }
}
