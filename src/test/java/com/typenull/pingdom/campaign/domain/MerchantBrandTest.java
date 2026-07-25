package com.typenull.pingdom.campaign.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MerchantBrandTest {

    @Test
    void trimsBrandFields() {
        MerchantBrand brand = MerchantBrand.create(
                10L,
                " 핑덤 ",
                " 브랜드 설명 ",
                null,
                LocalDateTime.of(2026, 8, 1, 12, 0)
        );

        assertThat(brand.getName()).isEqualTo("핑덤");
        assertThat(brand.getDescription()).isEqualTo("브랜드 설명");
        assertThat(brand.getLogoUrl()).isNull();
    }

    @Test
    void blankNameIsRejected() {
        assertThatThrownBy(() -> MerchantBrand.create(
                10L, " ", null, null, LocalDateTime.now()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
