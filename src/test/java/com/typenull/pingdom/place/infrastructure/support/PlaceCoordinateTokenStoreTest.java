package com.typenull.pingdom.place.infrastructure.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import org.junit.jupiter.api.Test;

class PlaceCoordinateTokenStoreTest {

    private final PlaceCoordinateTokenStore tokenStore = new PlaceCoordinateTokenStore();

    @Test
    void userProvidedCoordinatesRemainUserPinEvenWithKakaoPlaceId() {
        String token = tokenStore.putUserPin(1L, "27414316", 35.1894, 128.0789);

        assertThat(tokenStore.consume(token).geocodingSource()).isEqualTo(GeocodingSource.USER_PIN);
    }

    @Test
    void verifiedKakaoPathRecordsKakaoProvenance() {
        String token = tokenStore.putVerifiedKakao(1L, " 27414316 ", 35.1894, 128.0789);

        PlaceCoordinateTokenStore.Entry entry = tokenStore.consume(token);
        assertThat(entry.kakaoPlaceId()).isEqualTo("27414316");
        assertThat(entry.geocodingSource()).isEqualTo(GeocodingSource.KAKAO);
    }

    @Test
    void verifiedKakaoPathRejectsMissingPlaceId() {
        assertThatThrownBy(() -> tokenStore.putVerifiedKakao(1L, " ", 35.1894, 128.0789))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
