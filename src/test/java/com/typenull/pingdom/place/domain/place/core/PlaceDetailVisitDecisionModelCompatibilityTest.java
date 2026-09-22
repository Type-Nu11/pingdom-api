package com.typenull.pingdom.place.domain.place.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlaceDetailVisitDecisionModelCompatibilityTest {

    /** 추가 방문 판단 필드를 지정하지 않은 장소가 노출·운영 중·미검증 기본값과 빈 영업시간·카테고리를 제공하는지 확인. */
    @Test
    void defaultsLegacyVisitDecisionFields() {
        MapPlace place = legacyPlace();

        assertThat(place.isVisibleInDiscovery()).isTrue();
        assertThat(place.getOperatingStatus()).isEqualTo(PlaceOperatingStatus.OPERATING);
        assertThat(place.getPrimaryInformationSource()).isEqualTo(PlaceInformationSourceType.LEGACY);
        assertThat(place.getInformationVerificationStatus())
                .isEqualTo(PlaceInformationVerificationStatus.UNVERIFIED);
        assertThat(place.currentRegularOperatingHours()).isEmpty();
        assertThat(place.currentOperatingExceptions()).isEmpty();
        assertThat(place.currentTouristCategories()).isEmpty();
    }

    /** 임시 휴업은 탐색 노출을 유지하면서 영업 중 판정만 false로 바꾸는지 확인. */
    @Test
    void keepsTemporaryClosureVisible() {
        MapPlace place = legacyPlace();
        place.updateOperatingStatus(PlaceOperatingStatus.TEMPORARILY_CLOSED, LocalDateTime.now());

        assertThat(place.isVisibleInDiscovery()).isTrue();
        assertThat(place.isOperating()).isFalse();
        assertThat(place.getOperatingStatus()).isEqualTo(PlaceOperatingStatus.TEMPORARILY_CLOSED);
    }

    /** 영구 폐업 상태를 구별 가능한 enum으로 보존하고 노출 상태는 그대로 둔 채 영업 중 판정을 false로 만드는지 확인. */
    @Test
    void preservesPermanentClosureStatus() {
        MapPlace place = legacyPlace();
        place.updateOperatingStatus(PlaceOperatingStatus.PERMANENTLY_CLOSED, LocalDateTime.now());

        assertThat(place.isVisibleInDiscovery()).isTrue();
        assertThat(place.isOperating()).isFalse();
        assertThat(place.getOperatingStatus()).isEqualTo(PlaceOperatingStatus.PERMANENTLY_CLOSED);
    }

    /** 노출을 숨겨도 저장된 OPERATING 상태가 바뀌지 않는지 확인. */
    @Test
    void hidesPlaceWithoutChangingOperation() {
        MapPlace place = legacyPlace();
        place.updateDiscoveryStatus(PlaceDiscoveryStatus.HIDDEN);

        assertThat(place.isVisibleInDiscovery()).isFalse();
        assertThat(place.getOperatingStatus()).isEqualTo(PlaceOperatingStatus.OPERATING);
    }

    /** 관리자 검증 정보·검증자·시각을 추가해도 기존 장소 ID가 유지되는지 확인. */
    @Test
    void preservesIdentityDuringVerification() {
        MapPlace place = legacyPlace();
        LocalDateTime verifiedAt = LocalDateTime.of(2026, 8, 5, 12, 0);

        place.updateInformationVerification(
                PlaceInformationSourceType.ADMIN,
                PlaceInformationVerificationStatus.ADMIN_VERIFIED,
                99L,
                verifiedAt,
                verifiedAt
        );

        assertThat(place.getId()).isEqualTo(100L);
        assertThat(place.getPrimaryInformationSource()).isEqualTo(PlaceInformationSourceType.ADMIN);
        assertThat(place.getInformationVerificationStatus())
                .isEqualTo(PlaceInformationVerificationStatus.ADMIN_VERIFIED);
        assertThat(place.getInformationVerifiedByAdminUserId()).isEqualTo(99L);
        assertThat(place.getInformationVerifiedAt()).isEqualTo(verifiedAt);
    }

    /** 영문명과 설명을 갱신하면서 빈 카테고리를 전달하면 조회 결과도 빈 집합인지 확인. 불변성은 이 메서드의 검증 범위에서 제외. */
    @Test
    void keepsEmptyTouristCategories() {
        MapPlace place = legacyPlace();
        place.updateTouristInformation("Legacy Place", "방문 결정에 필요한 장소 설명", Set.of());

        assertThat(place.currentTouristCategories()).isEmpty();
    }

    /** 기본 카테고리 집합에 직접 추가하려 하면 UnsupportedOperationException으로 막히는지 확인. */
    @Test
    void rejectsLegacyCategoryMutation() {
        MapPlace place = legacyPlace();

        assertThatThrownBy(() -> place.currentTouristCategories().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** 새 방문 판단 필드를 지정하지 않은 기존 형태 장소를 만들어 기본값 호환성을 확인. */
    private MapPlace legacyPlace() {
        return MapPlace.builder()
                .id(100L)
                .name("기존 장소")
                .address("서울특별시 중구 테스트로 1")
                .latitude(37.5665)
                .longitude(126.9780)
                .registrant("legacy-user")
                .build();
    }
}
