package com.typenull.pingdom.community.domain;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CommunityCountryTest {
    @Test void acceptsOnlyDocumentedFilterCodes() {
        assertThat(CommunityCountry.parseFilter(null)).isNull();
        assertThat(CommunityCountry.parseFilter("KR")).isEqualTo(CommunityCountry.KR);
        assertThatThrownBy(() -> CommunityCountry.parseFilter("한국")).hasMessage("지원하지 않는 국가 필터입니다.");
        assertThatThrownBy(() -> CommunityCountry.parseFilter("")).hasMessage("지원하지 않는 국가 필터입니다.");
    }
    @Test void normalizesExistingMemberCountryNames() {
        assertThat(CommunityCountry.normalize(" South Korea ")).isEqualTo("KR");
        assertThat(CommunityCountry.normalize("대한민국")).isEqualTo("KR");
        assertThat(CommunityCountry.normalize("미국")).isEqualTo("US");
        assertThat(CommunityCountry.normalize("UNKNOWN")).isNull();
        assertThat(CommunityCountry.normalize(null)).isNull();
    }
}
