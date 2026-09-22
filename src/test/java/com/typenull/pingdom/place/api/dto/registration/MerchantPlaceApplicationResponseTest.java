package com.typenull.pingdom.place.api.dto.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachment;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachmentType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationCategory;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationOperatingStatus;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationTag;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MerchantPlaceApplicationResponseTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 12, 0);

    /** 사업자 신규 신청 응답이 유형·장소명·태그와 월요일 영업시간을 보존하는지 확인. */
    @Test
    void mapsNewPlaceApplication() throws Exception {
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

    /** 기존 장소 운영권 신청은 신규 장소 입력 응답을 null로 반환하는지 확인. */
    @Test
    void omitsClaimNewPlaceDetails() {
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

    /** 필수 첨부를 갖춘 신청을 제출·승인·완료하면 응답에 COMPLETED와 연결 장소 ID가 반영되는지 확인. */
    @Test
    void returnsCompletedApplicationPlace() {
        PlaceRegistrationApplication application = newPlaceDraft();
        application.replaceAttachments(List.of(
                attachment(application, PlaceRegistrationAttachmentType.BUSINESS_REGISTRATION, "business"),
                attachment(application, PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT, "identity"),
                attachment(application, PlaceRegistrationAttachmentType.REPRESENTATIVE_IMAGE, "image")
        ), NOW);
        application.submit(NOW);
        application.approve(99L, "승인", NOW);
        application.complete(30L, NOW);

        MerchantPlaceApplicationResponse response = MerchantPlaceApplicationResponse.from(application, objectMapper());

        assertThat(response.status()).isEqualTo(PlaceRegistrationStatus.COMPLETED);
        assertThat(response.placeId()).isEqualTo(30L);
    }

    /** 고정 장소 정보와 영어 메뉴 태그를 가진 사업자 신규 신청 초안을 생성. */
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

    /** 영업일 JSON 직렬화·역직렬화에 필요한 JavaTimeModule을 등록. */
    private ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /** 문서 유형마다 다른 해시를 가진 필수 첨부를 만들어 제출 조건을 충족시킴. 파일 저장소 호출 없이 메타데이터만 구성. */
    private PlaceRegistrationAttachment attachment(
            PlaceRegistrationApplication application,
            PlaceRegistrationAttachmentType documentType,
            String key
    ) {
        return PlaceRegistrationAttachment.create(
                application,
                key,
                documentType,
                "private/test/" + key,
                key + ".pdf",
                "application/pdf",
                10L,
                switch (documentType) {
                    case BUSINESS_REGISTRATION -> "a".repeat(64);
                    case IDENTITY_DOCUMENT -> "b".repeat(64);
                    case REPRESENTATIVE_IMAGE -> "c".repeat(64);
                },
                1L,
                NOW,
                null,
                0
        );
    }
}
