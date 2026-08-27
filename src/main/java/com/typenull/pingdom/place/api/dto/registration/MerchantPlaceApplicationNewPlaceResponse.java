package com.typenull.pingdom.place.api.dto.registration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationCategory;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationTag;
import java.util.List;
import java.util.Set;

/** NEW_PLACE 초안을 다시 열어 수정할 때 사용하는 저장된 장소 입력값입니다. */
public record MerchantPlaceApplicationNewPlaceResponse(
        String placeName,
        PlaceRegistrationCategory category,
        double latitude,
        double longitude,
        String roadAddress,
        String jibunAddress,
        String postalCode,
        String description,
        String businessContactPhone,
        String applicantContactPhone,
        Set<PlaceRegistrationTag> tags,
        String timezone,
        List<PlaceRegistrationOperatingDay> operatingDays
) {
    public static MerchantPlaceApplicationNewPlaceResponse from(
            PlaceRegistrationApplication application,
            ObjectMapper objectMapper
    ) {
        return new MerchantPlaceApplicationNewPlaceResponse(
                application.getPlaceName(),
                application.getCategory(),
                application.getLatitude(),
                application.getLongitude(),
                application.getRoadAddress(),
                application.getJibunAddress(),
                application.getPostalCode(),
                application.getDescription(),
                application.getBusinessContactPhone(),
                application.getApplicantContactPhone(),
                application.getTags(),
                application.getTimezone(),
                operatingDays(application, objectMapper)
        );
    }

    private static List<PlaceRegistrationOperatingDay> operatingDays(
            PlaceRegistrationApplication application,
            ObjectMapper objectMapper
    ) {
        try {
            return objectMapper.readValue(
                    application.getOperatingScheduleJson(),
                    new TypeReference<List<PlaceRegistrationOperatingDay>>() {}
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 신규 장소 영업일 정보를 읽을 수 없습니다.", exception);
        }
    }
}
