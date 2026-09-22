package com.typenull.pingdom.place.domain.place.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceMediaTest {

    /** 방문 증빙 미디어는 원본 게시글 식별자 없이 생성할 수 없는지 오류 메시지와 함께 확인. */
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

    /** 탐색용 미디어는 이미지 URL을 정리하고 원본 게시글 ID를 비우며 음수 표시 순서를 0으로 보정하는지 확인. */
    @Test
    void normalizesExplorationMedia() {
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

    /** 공백 이미지 URL로 탐색용 미디어를 만들 수 없는지 확인. */
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

    /** 미디어 목적·입력 검증에 필요한 장소 값을 메모리에 생성. */
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
