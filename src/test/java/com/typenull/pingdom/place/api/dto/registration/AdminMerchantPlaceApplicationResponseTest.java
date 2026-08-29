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

class AdminMerchantPlaceApplicationResponseTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 29, 16, 0);

    @Test
    void returnsStoredNewPlaceFieldsForNewPlaceApplication() throws Exception {
        PlaceRegistrationApplication application = newPlaceDraft(Set.of(PlaceRegistrationTag.ENGLISH_MENU_AVAILABLE));
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

        AdminMerchantPlaceApplicationResponse response = response(application);

        assertThat(response.applicationType()).isEqualTo(MerchantPlaceApplicationType.NEW_PLACE);
        assertThat(response.newPlace()).isNotNull();
        assertThat(response.newPlace().placeName()).isEqualTo("핑덤 카페");
        assertThat(response.newPlace().category()).isEqualTo(PlaceRegistrationCategory.CAFE);
        assertThat(response.newPlace().latitude()).isEqualTo(37.5665);
        assertThat(response.newPlace().longitude()).isEqualTo(126.9780);
        assertThat(response.newPlace().roadAddress()).isEqualTo("서울특별시 중구 세종대로 110");
        assertThat(response.newPlace().jibunAddress()).isEqualTo("서울특별시 중구 태평로1가 31");
        assertThat(response.newPlace().postalCode()).isEqualTo("04524");
        assertThat(response.newPlace().description()).isEqualTo("테스트 장소 설명");
        assertThat(response.newPlace().businessContactPhone()).isEqualTo("+821012345678");
        assertThat(response.newPlace().applicantContactPhone()).isEqualTo("+821098765432");
        assertThat(response.newPlace().tags()).containsExactly(PlaceRegistrationTag.ENGLISH_MENU_AVAILABLE);
        assertThat(response.newPlace().timezone()).isEqualTo("Asia/Seoul");
        assertThat(response.newPlace().operatingDays()).containsExactly(new PlaceRegistrationOperatingDay(
                DayOfWeek.MONDAY,
                PlaceRegistrationOperatingStatus.OPEN,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                List.of()
        ));
    }

    @Test
    void returnsNullNewPlaceForExistingPlaceClaim() {
        PlaceRegistrationApplication application = newPlaceDraft(Set.of());
        application.configureMerchantSubmission(
                MerchantPlaceApplicationType.EXISTING_PLACE_CLAIM,
                "홍길동", "핑덤", "encrypted-business-registration-number", "핑덤 카페",
                "owner@pingdom.test", "소개", "+821012345678", 30L, 20L, "운영권 이전", NOW
        );

        AdminMerchantPlaceApplicationResponse response = response(application);

        assertThat(response.applicationType()).isEqualTo(MerchantPlaceApplicationType.EXISTING_PLACE_CLAIM);
        assertThat(response.newPlace()).isNull();
    }

    @Test
    void returnsEmptyCollectionsWhenTagsAndOperatingDaysAreNotProvided() {
        AdminMerchantPlaceApplicationResponse response = response(newPlaceDraft(Set.of()));

        assertThat(response.newPlace().tags()).isEmpty();
        assertThat(response.newPlace().operatingDays()).isEmpty();
    }

    private AdminMerchantPlaceApplicationResponse response(PlaceRegistrationApplication application) {
        return AdminMerchantPlaceApplicationResponse.from(application, "1234567890", List.of(), objectMapper());
    }

    private PlaceRegistrationApplication newPlaceDraft(Set<PlaceRegistrationTag> tags) {
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
                tags,
                NOW
        );
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
