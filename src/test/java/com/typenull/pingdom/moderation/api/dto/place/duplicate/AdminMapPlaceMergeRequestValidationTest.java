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

    /**
     * HTTP 계층 없이 병합 요청의 Bean Validation 제약을 직접 검사할 validator를 초기화한다.
     */
    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    /**
     * 원본 장소 ID가 0이면 위반 필드가 sourcePlaceId뿐인지 검증한다.
     */
    @Test
    @DisplayName("원본 장소 ID는 양수여야 한다")
    void sourcePlaceIdIsPositive() {
        AdminMapPlaceMergeRequest request = new AdminMapPlaceMergeRequest(0L, 2L, null);

        assertThat(violatingProperties(request)).containsExactly("sourcePlaceId");
    }

    /**
     * 대상 장소 ID가 0이면 위반 필드가 targetPlaceId뿐인지 검증한다.
     */
    @Test
    @DisplayName("대상 장소 ID는 양수여야 한다")
    void targetPlaceIdIsPositive() {
        AdminMapPlaceMergeRequest request = new AdminMapPlaceMergeRequest(1L, 0L, null);

        assertThat(violatingProperties(request)).containsExactly("targetPlaceId");
    }

    /**
     * 후보 ID는 null일 때 허용하고 음수로 지정하면 candidateId 필드 위반을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("중복 장소 후보 ID는 선택값이지만 입력하면 양수여야 한다")
    void candidateIdIsOptionalAndPositive() {
        assertThat(violatingProperties(new AdminMapPlaceMergeRequest(1L, 2L, null))).isEmpty();
        assertThat(violatingProperties(new AdminMapPlaceMergeRequest(1L, 2L, -1L)))
                .containsExactly("candidateId");
    }

    /**
     * 검증 위반의 필드 경로만 집합으로 추출해 오류 메시지나 반환 순서와 무관하게 제약 대상을 비교한다.
     */
    private Set<String> violatingProperties(AdminMapPlaceMergeRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}
