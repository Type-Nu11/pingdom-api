package com.typenull.pingdom.place.domain.place.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TouristPlaceCardModelCompatibilityTest {

    @Test
    void legacyPlaceDefaultsRemainSafeForTouristCardExposure() {
        MapPlace place = MapPlace.builder()
                .id(100L)
                .name("기존 장소")
                .address("서울시 중구 테스트로 1")
                .latitude(37.5637d)
                .longitude(126.9829d)
                .registrant("legacy-user")
                .build();

        assertThat(place.getDiscoveryStatus())
                .as("기존 장소는 마이그레이션 후 관광객 탐색에 노출 가능한 상태를 유지해야 한다")
                .isEqualTo(PlaceDiscoveryStatus.VISIBLE);
        assertThat(place.getOperatingStatus())
                .as("기존 장소의 운영 상태 기본값은 운영 중이어야 한다")
                .isEqualTo(PlaceOperatingStatus.OPERATING);
        assertThat(place.getPrimaryInformationSource())
                .as("기존 장소의 출처는 LEGACY로 명확히 식별되어야 한다")
                .isEqualTo(PlaceInformationSourceType.LEGACY);
        assertThat(place.getInformationVerificationStatus())
                .as("기존 장소의 검증 상태는 미검증으로 보수적으로 표시되어야 한다")
                .isEqualTo(PlaceInformationVerificationStatus.UNVERIFIED);
        assertThat(place.currentTouristCategories())
                .as("기존 장소에 관광 카테고리가 없어도 카드 조회가 실패하지 않아야 한다")
                .isEqualTo(Set.of());
    }

    @Test
    void touristCardEnumsUseStablePersistedNames() {
        assertThat(PlaceDiscoveryStatus.values())
                .extracting(Enum::name)
                .containsExactly("VISIBLE", "HIDDEN");
        assertThat(PlaceOperatingStatus.values())
                .extracting(Enum::name)
                .contains("OPERATING", "TEMPORARILY_CLOSED", "PERMANENTLY_CLOSED");
        assertThat(PlaceInformationSourceType.values())
                .extracting(Enum::name)
                .contains("LEGACY", "ADMIN", "MERCHANT_OWNER", "USER_REPORT");
        assertThat(PlaceInformationVerificationStatus.values())
                .extracting(Enum::name)
                .contains("UNVERIFIED", "OWNER_SUBMITTED", "ADMIN_VERIFIED", "DISPUTED", "REJECTED");
    }
}
