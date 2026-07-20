package com.typenull.pingdom.place.domain.place.core;

import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingException;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingTimeRange;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingHour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MapPlaceTest {

    @Test
    void defaultsGeocodingSourceToLegacy() {
        MapPlace mapPlace = MapPlace.builder().build();

        assertThat(mapPlace.getGeocodingSource()).isEqualTo(GeocodingSource.LEGACY);
    }

    @Test
    void defaultsOperatingStatusToOperatingAndUpdatesConfirmationTime() {
        MapPlace mapPlace = MapPlace.builder().build();
        LocalDateTime checkedAt = LocalDateTime.of(2026, 7, 13, 10, 30);

        assertThat(mapPlace.getOperatingStatus()).isEqualTo(PlaceOperatingStatus.OPERATING);
        assertThat(mapPlace.getOperatingStatusCheckedAt()).isNull();
        assertThat(mapPlace.isOperating()).isTrue();

        mapPlace.updateOperatingStatus(PlaceOperatingStatus.TEMPORARILY_CLOSED, checkedAt);

        assertThat(mapPlace.getOperatingStatus()).isEqualTo(PlaceOperatingStatus.TEMPORARILY_CLOSED);
        assertThat(mapPlace.getOperatingStatusCheckedAt()).isEqualTo(checkedAt);
        assertThat(mapPlace.isOperating()).isFalse();

        assertThatThrownBy(() -> mapPlace.updateOperatingStatus(null, checkedAt))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("operatingStatus must not be null");
    }

    @Test
    void defaultsDiscoveryStatusToVisibleAndUpdatesStatus() {
        MapPlace mapPlace = MapPlace.builder().build();

        assertThat(mapPlace.getDiscoveryStatus()).isEqualTo(PlaceDiscoveryStatus.VISIBLE);
        assertThat(mapPlace.isVisibleInDiscovery()).isTrue();

        mapPlace.updateDiscoveryStatus(PlaceDiscoveryStatus.HIDDEN);

        assertThat(mapPlace.getDiscoveryStatus()).isEqualTo(PlaceDiscoveryStatus.HIDDEN);
        assertThat(mapPlace.isVisibleInDiscovery()).isFalse();
        assertThatThrownBy(() -> mapPlace.updateDiscoveryStatus(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("discoveryStatus must not be null");
    }

    @Test
    void defaultsInformationVerificationToLegacyUnverifiedAndUpdatesSummary() {
        MapPlace mapPlace = MapPlace.builder().build();
        LocalDateTime verifiedAt = LocalDateTime.of(2026, 7, 20, 11, 0);

        assertThat(mapPlace.getPrimaryInformationSource()).isEqualTo(PlaceInformationSourceType.LEGACY);
        assertThat(mapPlace.getInformationVerificationStatus())
                .isEqualTo(PlaceInformationVerificationStatus.UNVERIFIED);
        assertThat(mapPlace.getInformationVerifiedAt()).isNull();
        assertThat(mapPlace.getInformationVerifiedByAdminUserId()).isNull();
        assertThat(mapPlace.getInformationEvidenceUpdatedAt()).isNull();

        mapPlace.updateInformationVerification(
                PlaceInformationSourceType.ADMIN,
                PlaceInformationVerificationStatus.ADMIN_VERIFIED,
                99L,
                verifiedAt,
                verifiedAt
        );

        assertThat(mapPlace.getPrimaryInformationSource()).isEqualTo(PlaceInformationSourceType.ADMIN);
        assertThat(mapPlace.getInformationVerificationStatus())
                .isEqualTo(PlaceInformationVerificationStatus.ADMIN_VERIFIED);
        assertThat(mapPlace.getInformationVerifiedByAdminUserId()).isEqualTo(99L);
        assertThat(mapPlace.getInformationVerifiedAt()).isEqualTo(verifiedAt);
        assertThat(mapPlace.getInformationEvidenceUpdatedAt()).isEqualTo(verifiedAt);

        assertThatThrownBy(() -> mapPlace.updateInformationVerification(
                null,
                PlaceInformationVerificationStatus.UNVERIFIED,
                null,
                null,
                null
        )).isInstanceOf(NullPointerException.class)
                .hasMessage("primaryInformationSource must not be null");
        assertThatThrownBy(() -> mapPlace.updateInformationVerification(
                PlaceInformationSourceType.LEGACY,
                null,
                null,
                null,
                null
        )).isInstanceOf(NullPointerException.class)
                .hasMessage("informationVerificationStatus must not be null");
    }

    @Test
    void replaceOperatingScheduleDefensivelyCopiesRegularHoursAndExceptions() {
        MapPlace mapPlace = MapPlace.builder().build();
        Set<PlaceRegularOperatingHour> regularHours = new LinkedHashSet<>(Set.of(
                PlaceRegularOperatingHour.of(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0))
        ));
        List<PlaceOperatingException> exceptions = new ArrayList<>(List.of(
                PlaceOperatingException.closed(mapPlace, LocalDate.of(2026, 8, 15)),
                PlaceOperatingException.customHours(
                        mapPlace,
                        LocalDate.of(2026, 8, 16),
                        Set.of(PlaceOperatingTimeRange.of(LocalTime.of(10, 0), LocalTime.of(16, 0)))
                )
        ));

        mapPlace.replaceOperatingSchedule(regularHours, exceptions);
        regularHours.clear();
        exceptions.clear();

        assertThat(mapPlace.currentRegularOperatingHours()).containsExactly(
                PlaceRegularOperatingHour.of(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0))
        );
        assertThat(mapPlace.currentOperatingExceptions()).hasSize(2);
        assertThat(mapPlace.currentOperatingExceptions().get(0).isClosed()).isTrue();
        assertThat(mapPlace.currentOperatingExceptions().get(1).currentHours()).containsExactly(
                PlaceOperatingTimeRange.of(LocalTime.of(10, 0), LocalTime.of(16, 0))
        );
        assertThatThrownBy(() -> mapPlace.currentRegularOperatingHours().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> mapPlace.currentOperatingExceptions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void updateGeocodingReplacesAddressCoordinatesAndSourceTogether() {
        MapPlace mapPlace = MapPlace.builder()
                .address("기존 주소")
                .latitude(35.0)
                .longitude(128.0)
                .build();

        mapPlace.updateGeocoding(
                "경상남도 진주시 남강로 626",
                "경상남도 진주시 남강로 626",
                "경상남도 진주시 본성동 500-8",
                "52692",
                35.1894,
                128.0789,
                null,
                GeocodingSource.ADMIN
        );

        assertThat(mapPlace.getAddress()).isEqualTo("경상남도 진주시 남강로 626");
        assertThat(mapPlace.getRoadAddress()).isEqualTo("경상남도 진주시 남강로 626");
        assertThat(mapPlace.getJibunAddress()).isEqualTo("경상남도 진주시 본성동 500-8");
        assertThat(mapPlace.getPostalCode()).isEqualTo("52692");
        assertThat(mapPlace.getLatitude()).isEqualTo(35.1894);
        assertThat(mapPlace.getLongitude()).isEqualTo(128.0789);
        assertThat(mapPlace.getGeocodingSource()).isEqualTo(GeocodingSource.ADMIN);
    }

    @Test
    void currentTouristCategoriesReturnsEmptyImmutableSetForDefaultAndNullValues() {
        MapPlace defaultMapPlace = MapPlace.builder().build();
        MapPlace nullCategoriesMapPlace = MapPlace.builder()
                .touristCategories(null)
                .build();

        Set<TouristCategory> categories = defaultMapPlace.currentTouristCategories();

        assertThat(categories).isEmpty();
        assertThat(nullCategoriesMapPlace.currentTouristCategories()).isEmpty();
        assertThatThrownBy(() -> categories.add(TouristCategory.OTHER))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void updateTouristInformationReplacesCategoriesWithDefensiveCopy() {
        MapPlace mapPlace = MapPlace.builder()
                .touristCategories(new LinkedHashSet<>(List.of(
                        TouristCategory.K_POP,
                        TouristCategory.CAFE
                )))
                .build();
        Set<TouristCategory> updatedCategories = new LinkedHashSet<>(List.of(
                TouristCategory.FOOD,
                TouristCategory.NIGHTLIFE
        ));

        mapPlace.updateTouristInformation(
                "Gwangjang Market",
                "A market known for Korean street food.",
                updatedCategories
        );
        updatedCategories.clear();

        assertThat(mapPlace.getEnglishName()).isEqualTo("Gwangjang Market");
        assertThat(mapPlace.getTouristSummary()).isEqualTo("A market known for Korean street food.");
        assertThat(mapPlace.currentTouristCategories())
                .containsExactly(TouristCategory.FOOD, TouristCategory.NIGHTLIFE);
        assertThat(mapPlace.hasTouristInformationGuard()).isTrue();
        assertThatThrownBy(() -> mapPlace.currentTouristCategories().add(TouristCategory.OTHER))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void updateTouristInformationTreatsNullCategoriesAsEmptySet() {
        MapPlace mapPlace = MapPlace.builder()
                .touristCategories(Set.of(TouristCategory.EXHIBITION))
                .build();

        mapPlace.updateTouristInformation(null, null, null);

        assertThat(mapPlace.currentTouristCategories()).isEmpty();
        assertThat(mapPlace.hasTouristInformationGuard()).isFalse();
    }

    @Test
    void updateTouristInformationActivatesRollbackGuardForScalarOnlyInformation() {
        MapPlace mapPlace = MapPlace.builder().build();

        mapPlace.updateTouristInformation("Jinju Castle", null, Set.of());

        assertThat(mapPlace.currentTouristCategories()).isEmpty();
        assertThat(mapPlace.hasTouristInformationGuard()).isTrue();
    }
}
