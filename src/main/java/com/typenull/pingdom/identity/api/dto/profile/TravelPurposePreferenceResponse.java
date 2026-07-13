package com.typenull.pingdom.identity.api.dto.profile;

import com.typenull.pingdom.identity.domain.TravelPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Schema(description = "여행 목적 선호 응답")
public record TravelPurposePreferenceResponse(
        @Schema(description = "선호 여행 목적 목록", example = "[\"K_POP\", \"FOOD\"]")
        Set<TravelPurpose> travelPurposes
) {

    public TravelPurposePreferenceResponse {
        travelPurposes = travelPurposes == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(travelPurposes));
    }
}
