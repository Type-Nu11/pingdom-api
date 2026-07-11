package com.typenull.pingdom.place.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
