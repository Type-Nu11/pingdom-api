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

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void NAVER_provider와_필수_입력이_있으면_검증을_통과한다() {
        Set<String> invalidFields = invalidFields(new MapLinkConversionRequest(
                MapLinkConversionType.DIRECTIONS, "NAVER", "request-1"));

        assertThat(invalidFields).isEmpty();
    }

    @Test
    void provider와_linkType과_requestId의_필수값과_길이를_검증한다() {
        Set<String> invalidFields = invalidFields(new MapLinkConversionRequest(
                null, " ", " "));
        Set<String> longProviderInvalidFields = invalidFields(new MapLinkConversionRequest(
                MapLinkConversionType.DIRECTIONS, "N".repeat(31), "request-1"));

        assertThat(invalidFields).containsExactlyInAnyOrder("linkType", "provider", "requestId");
        assertThat(longProviderInvalidFields).containsExactly("provider");
    }

    private Set<String> invalidFields(MapLinkConversionRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
