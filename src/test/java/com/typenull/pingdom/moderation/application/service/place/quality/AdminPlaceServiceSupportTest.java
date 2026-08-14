package com.typenull.pingdom.moderation.application.service.place.quality;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 장소 서비스 공통 변환 함수의 기본 계약을 고정하는 단위 테스트다. */
class AdminPlaceServiceSupportTest {

    @Test
    void trimToNullNormalizesInput() {
        assertThat(AdminPlaceServiceSupport.trimToNull("  place  ")).isEqualTo("place");
        assertThat(AdminPlaceServiceSupport.trimToNull(" ")).isNull();
    }

    @Test
    void normalizeTouristCategoriesHandlesNull() {
        assertThat(AdminPlaceServiceSupport.normalizeTouristCategories(null)).isEmpty();
    }
}
