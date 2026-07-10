package com.typenull.pingdom.place.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MapPlaceTest {

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
