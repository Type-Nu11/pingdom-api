package com.typenull.pingdom.place.domain.place.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TouristPlaceCardModelCompatibilityTest {

    /** 추가 필드를 생략한 장소가 관광 카드에 필요한 노출·영업·출처·미검증 기본값과 빈 카테고리를 제공하는지 확인. DB 마이그레이션 실행은 검증 범위에서 제외. */
    @Test
    void preservesLegacyCardDefaults() {
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

    /** 노출·영업·출처·검증 enum의 기존 이름이 유지되는지 확인해 저장 문자열 계약 변경을 감지. */
    @Test
    void preservesTouristCardEnumNames() {
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
