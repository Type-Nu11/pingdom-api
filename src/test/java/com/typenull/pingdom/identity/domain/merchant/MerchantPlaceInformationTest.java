package com.typenull.pingdom.identity.domain.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MerchantPlaceInformationTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 5, 12, 0);
    private static final LocalDateTime UPDATED_AT = CREATED_AT.plusHours(1);

    /**
     * 장소 정보 갱신이 소개·전화·웹/예약 URL·수정자를 바꾸고 생성 시각은 보존하며 수정 시각만 갱신하는지 검증한다.
     */
    @Test
    void updatesMerchantManagedInformation() {
        MerchantPlaceInformation information = MerchantPlaceInformation.create(
                10L,
                "  K-컬처 체험 공간  ",
                "  010-1234-5678  ",
                "https://example.com/place",
                "https://example.com/reserve",
                20L,
                CREATED_AT
        );

        information.update(
                "새로운 소개",
                "010-9876-5432",
                "https://example.com/new-place",
                "https://example.com/new-reserve",
                21L,
                UPDATED_AT
        );

        assertThat(information.getPlaceId()).isEqualTo(10L);
        assertThat(information.getDescription()).isEqualTo("새로운 소개");
        assertThat(information.getContactPhone()).isEqualTo("010-9876-5432");
        assertThat(information.getWebsiteUrl()).isEqualTo("https://example.com/new-place");
        assertThat(information.getReservationUrl()).isEqualTo("https://example.com/new-reserve");
        assertThat(information.getUpdatedByUserId()).isEqualTo(21L);
        assertThat(information.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(information.getUpdatedAt()).isEqualTo(UPDATED_AT);
    }

    /**
     * 소개·전화·웹/예약 URL의 공백 또는 빈 문자열을 null로 정규화하는지 검증한다.
     */
    @Test
    void normalizesBlankMerchantInformation() {
        MerchantPlaceInformation information = MerchantPlaceInformation.create(
                10L,
                "  ",
                "",
                null,
                "   ",
                20L,
                CREATED_AT
        );

        assertThat(information.getDescription()).isNull();
        assertThat(information.getContactPhone()).isNull();
        assertThat(information.getWebsiteUrl()).isNull();
        assertThat(information.getReservationUrl()).isNull();
    }
}
