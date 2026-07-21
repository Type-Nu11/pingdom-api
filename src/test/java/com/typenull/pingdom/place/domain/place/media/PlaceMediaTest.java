package com.typenull.pingdom.place.domain.place.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceMediaTest {

    @Test
    void verificationRequiresSourceMapImageId() {
        assertThatThrownBy(() -> PlaceMedia.verification(
                place(),
                "https://example.com/photo.jpg",
                "map/photo.jpg",
                null,
                null,
                null,
                LocalDateTime.now()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceMapImageId must not be null for verification media");
    }

    @Test
    void explorationDoesNotUseSourceMapImageId() {
        PlaceMedia media = PlaceMedia.exploration(
                place(),
                " https://example.com/place.jpg ",
                null,
                null,
                null,
                -1,
                LocalDateTime.of(2026, 7, 21, 10, 0)
        );

        assertThat(media.getPurpose()).isEqualTo(PlaceMediaPurpose.EXPLORATION);
        assertThat(media.getImageUrl()).isEqualTo("https://example.com/place.jpg");
        assertThat(media.getSourceMapImageId()).isNull();
        assertThat(media.getDisplayOrder()).isZero();
    }

    @Test
    void imageUrlMustNotBeBlank() {
        assertThatThrownBy(() -> PlaceMedia.exploration(
                place(),
                " ",
                null,
                null,
                null,
                0,
                LocalDateTime.now()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("imageUrl must not be blank");
    }

    private MapPlace place() {
        return MapPlace.builder()
                .name("미디어 장소")
                .address("경상남도 진주시 미디어로 1")
                .latitude(35.1801)
                .longitude(128.1078)
                .registrant("tester")
                .build();
    }
}
