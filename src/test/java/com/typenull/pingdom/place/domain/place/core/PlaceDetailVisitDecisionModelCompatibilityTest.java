package com.typenull.pingdom.place.domain.place.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlaceDetailVisitDecisionModelCompatibilityTest {

    @Test
    void legacyPlaceHasSafeDefaultsForVisitDecisionData() {
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

    @Test
    void temporaryClosureRemainsVisibleButIsNotOperating() {
        MapPlace place = legacyPlace();
        place.updateOperatingStatus(PlaceOperatingStatus.TEMPORARILY_CLOSED, LocalDateTime.now());

        assertThat(place.isVisibleInDiscovery()).isTrue();
        assertThat(place.isOperating()).isFalse();
        assertThat(place.getOperatingStatus()).isEqualTo(PlaceOperatingStatus.TEMPORARILY_CLOSED);
    }

    @Test
    void permanentClosureIsDistinguishableFromTemporaryClosure() {
        MapPlace place = legacyPlace();
        place.updateOperatingStatus(PlaceOperatingStatus.PERMANENTLY_CLOSED, LocalDateTime.now());

        assertThat(place.isVisibleInDiscovery()).isTrue();
        assertThat(place.isOperating()).isFalse();
        assertThat(place.getOperatingStatus()).isEqualTo(PlaceOperatingStatus.PERMANENTLY_CLOSED);
    }

    @Test
    void hiddenPlaceRemainsExcludedWithoutChangingItsStoredOperatingState() {
        MapPlace place = legacyPlace();
        place.updateDiscoveryStatus(PlaceDiscoveryStatus.HIDDEN);

        assertThat(place.isVisibleInDiscovery()).isFalse();
        assertThat(place.getOperatingStatus()).isEqualTo(PlaceOperatingStatus.OPERATING);
    }

    @Test
    void verificationMetadataCanBeAddedWithoutChangingLegacyPlaceIdentity() {
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

    @Test
    void touristCategoriesAreReturnedAsAnImmutableSnapshot() {
        MapPlace place = legacyPlace();
        place.updateTouristInformation("Legacy Place", "방문 결정에 필요한 장소 설명", Set.of());

        assertThat(place.currentTouristCategories()).isEmpty();
    }

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
