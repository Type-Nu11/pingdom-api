package com.typenull.pingdom.place.api.dto.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationCategory;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationOperatingStatus;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationTag;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MerchantPlaceApplicationResponseTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 12, 0);

    @Test
    void returnsStoredNewPlaceFieldsForNewPlaceApplication() throws Exception {
        PlaceRegistrationApplication application = newPlaceDraft();
        application.updateContactPhones("+821012345678", "+821098765432");
        application.updateOperatingSchedule(
                "Asia/Seoul",
                objectMapper().writeValueAsString(List.of(new PlaceRegistrationOperatingDay(
                        DayOfWeek.MONDAY,
                        PlaceRegistrationOperatingStatus.OPEN,
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0),
                        List.of()
                ))),
                NOW
        );

        MerchantPlaceApplicationResponse response = MerchantPlaceApplicationResponse.from(application, objectMapper());

        assertThat(response.applicationType()).isEqualTo(MerchantPlaceApplicationType.NEW_PLACE);
        assertThat(response.newPlace()).isNotNull();
        assertThat(response.newPlace().placeName()).isEqualTo("핑덤 카페");
        assertThat(response.newPlace().tags()).containsExactly(PlaceRegistrationTag.ENGLISH_MENU_AVAILABLE);
        assertThat(response.newPlace().operatingDays()).containsExactly(new PlaceRegistrationOperatingDay(
                DayOfWeek.MONDAY,
                PlaceRegistrationOperatingStatus.OPEN,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                List.of()
        ));
    }

    @Test
    void omitsNewPlaceFieldsForExistingPlaceClaim() {
        PlaceRegistrationApplication application = newPlaceDraft();
        application.configureMerchantSubmission(
                MerchantPlaceApplicationType.EXISTING_PLACE_CLAIM,
                "홍길동", "핑덤", "encrypted-business-registration-number", "핑덤 카페",
                "owner@pingdom.test", "소개", "+821012345678", 30L, 20L, "운영권 이전", NOW
        );

        MerchantPlaceApplicationResponse response = MerchantPlaceApplicationResponse.from(application, objectMapper());

        assertThat(response.applicationType()).isEqualTo(MerchantPlaceApplicationType.EXISTING_PLACE_CLAIM);
        assertThat(response.newPlace()).isNull();
    }

    private PlaceRegistrationApplication newPlaceDraft() {
        return PlaceRegistrationApplication.merchantPlaceDraft(
                1L,
                "핑덤 카페",
                PlaceRegistrationCategory.CAFE,
                37.5665,
                126.9780,
                "서울특별시 중구 세종대로 110",
                "서울특별시 중구 태평로1가 31",
                "04524",
                "테스트 장소 설명",
                Set.of(PlaceRegistrationTag.ENGLISH_MENU_AVAILABLE),
                NOW
        );
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
