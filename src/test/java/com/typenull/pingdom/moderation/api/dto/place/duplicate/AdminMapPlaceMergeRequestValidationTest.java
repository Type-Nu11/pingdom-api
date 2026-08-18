package com.typenull.pingdom.moderation.api.dto.place.duplicate;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminMapPlaceMergeRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("원본 장소 ID는 양수여야 한다")
    void sourcePlaceIdIsPositive() {
        AdminMapPlaceMergeRequest request = new AdminMapPlaceMergeRequest(0L, 2L, null);

        assertThat(violatingProperties(request)).containsExactly("sourcePlaceId");
    }

    @Test
    @DisplayName("대상 장소 ID는 양수여야 한다")
    void targetPlaceIdIsPositive() {
        AdminMapPlaceMergeRequest request = new AdminMapPlaceMergeRequest(1L, 0L, null);

        assertThat(violatingProperties(request)).containsExactly("targetPlaceId");
    }

    @Test
    @DisplayName("중복 장소 후보 ID는 선택값이지만 입력하면 양수여야 한다")
    void candidateIdIsOptionalAndPositive() {
        assertThat(violatingProperties(new AdminMapPlaceMergeRequest(1L, 2L, null))).isEmpty();
        assertThat(violatingProperties(new AdminMapPlaceMergeRequest(1L, 2L, -1L)))
                .containsExactly("candidateId");
    }

    private Set<String> violatingProperties(AdminMapPlaceMergeRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}
