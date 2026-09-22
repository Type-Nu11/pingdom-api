package com.typenull.pingdom.moderation.application.service.place.quality;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

/** 장소 서비스 공통 변환 함수의 기본 계약을 고정하는 단위 테스트다. */
class AdminPlaceServiceSupportTest {

    /**
     * 문자열 양끝 공백은 제거하고 공백뿐인 값은 null로 정규화하는지 검증한다.
     */
    @Test
    void trimToNullNormalizesInput() {
        assertThat(AdminPlaceServiceSupport.trimToNull("  place  ")).isEqualTo("place");
        assertThat(AdminPlaceServiceSupport.trimToNull(" ")).isNull();
    }

    /**
     * 관광 카테고리 입력이 null이면 빈 목록으로 정규화하는지 검증한다.
     */
    @Test
    void normalizeTouristCategoriesHandlesNull() {
        assertThat(AdminPlaceServiceSupport.normalizeTouristCategories(null)).isEmpty();
    }

    /**
     * 위도·경도를 Point로 변환할 때 X는 경도, Y는 위도이며 SRID가 WGS84의 4326인지 검증한다.
     */
    @Test
    void mapsCoordinatesToWgs84Point() {
        Point point = AdminPlaceServiceSupport.toPoint(35.1, 128.2);

        assertThat(point.getX()).isEqualTo(128.2);
        assertThat(point.getY()).isEqualTo(35.1);
        assertThat(point.getSRID()).isEqualTo(4326);
    }
}
