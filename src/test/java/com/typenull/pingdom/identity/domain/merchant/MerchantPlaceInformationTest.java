package com.typenull.pingdom.identity.domain.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MerchantPlaceInformationTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 5, 12, 0);
    private static final LocalDateTime UPDATED_AT = CREATED_AT.plusHours(1);

    @Test
    void createsAndUpdatesMerchantManagedInformation() {
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

    @Test
    void blankOptionalInformationIsStoredAsNull() {
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
