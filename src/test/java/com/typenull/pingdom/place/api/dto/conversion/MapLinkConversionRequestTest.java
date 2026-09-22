package com.typenull.pingdom.place.api.dto.conversion;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.place.domain.conversion.MapLinkConversionType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MapLinkConversionRequestTest {

    private static Validator validator;

    /** 테스트 클래스가 공유할 Bean Validation 검증기를 초기화. */
    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    /** NAVER provider와 길찾기 유형·요청 ID가 채워진 DTO에 검증 오류가 없는지 확인. */
    @Test
    void acceptsValidNaverRequest() {
        Set<String> invalidFields = invalidFields(new MapLinkConversionRequest(
                MapLinkConversionType.DIRECTIONS, "NAVER", "request-1"));

        assertThat(invalidFields).isEmpty();
    }

    /** 비어 있는 필수 세 필드를 거부하고 provider 길이 31자가 상한을 넘는지 검증. */
    @Test
    void rejectsMissingConversionFields() {
        Set<String> invalidFields = invalidFields(new MapLinkConversionRequest(
                null, " ", " "));
        Set<String> longProviderInvalidFields = invalidFields(new MapLinkConversionRequest(
                MapLinkConversionType.DIRECTIONS, "N".repeat(31), "request-1"));

        assertThat(invalidFields).containsExactlyInAnyOrder("linkType", "provider", "requestId");
        assertThat(longProviderInvalidFields).containsExactly("provider");
    }

    /** 위반 메시지 대신 property path를 모아 DTO 필드별 계약을 비교. */
    private Set<String> invalidFields(MapLinkConversionRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
