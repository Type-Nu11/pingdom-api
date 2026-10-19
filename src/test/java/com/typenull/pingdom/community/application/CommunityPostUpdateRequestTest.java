package com.typenull.pingdom.community.application;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.community.api.dto.CommunityPostUpdateRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class CommunityPostUpdateRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void omissionNullAndEmptyCollectionsHaveDistinctMeanings() throws Exception {
        var omitted = mapper.readValue("{\"title\":\"제목\"}", CommunityPostUpdateRequest.class);
        assertThat(omitted.getImageIds()).isNull();
        assertThat(omitted.isCountryCodeSpecified()).isFalse();
        var clear = mapper.readValue("{\"countryCode\":null,\"region\":null,\"imageIds\":[],\"placeIds\":[]}", CommunityPostUpdateRequest.class);
        assertThat(clear.isCountryCodeSpecified()).isTrue();
        assertThat(clear.isRegionSpecified()).isTrue();
        assertThat(clear.getImageIds()).isEmpty();
        assertThat(clear.getPlaceIds()).isEmpty();
        for (String field : new String[]{"title", "content", "categoryId", "imageIds", "placeIds"}) {
            assertThatThrownBy(() -> mapper.readValue("{\"" + field + "\":null}", CommunityPostUpdateRequest.class))
                    .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
        }
    }

    @Test void rejectsEmptyBlankOversizeAndInvalidIds() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String json : new String[]{"{}", "{\"title\":\" \",\"content\":\"본문\"}",
                    "{\"title\":\"" + "가".repeat(51) + "\"}", "{\"imageIds\":[0]}",
                    "{\"imageIds\":[null]}", "{\"countryCode\":\"kr\"}"}) {
                assertThat(validator.validate(mapper.readValue(json, CommunityPostUpdateRequest.class))).as(json).isNotEmpty();
            }
            assertThat(validator.validate(mapper.readValue("{\"region\":null}", CommunityPostUpdateRequest.class))).isEmpty();
        }
    }
}
