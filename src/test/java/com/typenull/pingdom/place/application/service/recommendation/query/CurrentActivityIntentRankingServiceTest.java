package com.typenull.pingdom.place.application.service.recommendation.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyList;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserCurrentActivityIntentRepository;
import com.typenull.pingdom.identity.domain.travel.CurrentActivityIntent;
import com.typenull.pingdom.identity.domain.travel.UserCurrentActivityIntent;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.candidate.PlaceRecommendationCandidateSource;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRecommendationCandidateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurrentActivityIntentRankingServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-23T00:00:00Z");

    @Mock
    private UserCurrentActivityIntentRepository currentActivityIntentRepository;

    @Mock
    private MapPlaceRecommendationCandidateRepository candidateRepository;

    @Test
    void 활성_의도와_일치하는_장소의_순위를_높인다() {
        Long userId = 7L;
        User user = User.builder().id(userId).build();
        UserCurrentActivityIntent intent = UserCurrentActivityIntent.create(
                user,
                CurrentActivityIntent.CAFE,
                LocalDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC)
        );
        when(currentActivityIntentRepository.findByUser_Id(userId)).thenReturn(Optional.of(intent));

        MapPlace food = place(1L, TouristCategory.FOOD);
        MapPlace cafe = place(2L, TouristCategory.CAFE);
        CurrentActivityIntentRankingService service = service();
        when(candidateRepository.findTouristCategoriesByPlaceIds(List.of(food.getId(), cafe.getId())))
                .thenReturn(List.of(row(food.getId(), TouristCategory.FOOD), row(cafe.getId(), TouristCategory.CAFE)));

        var result = service.apply(userId, List.of(candidate(food, 0.60d), candidate(cafe, 0.50d)));

        assertThat(result.intent()).isEqualTo(CurrentActivityIntent.CAFE);
        assertThat(result.candidates()).extracting(candidate -> candidate.place().getId())
                .containsExactly(cafe.getId(), food.getId());
    }

    @Test
    void EXPLORE는_순위와_적용_의도를_변경하지_않는다() {
        Long userId = 9L;
        User user = User.builder().id(userId).build();
        UserCurrentActivityIntent intent = UserCurrentActivityIntent.create(
                user,
                CurrentActivityIntent.EXPLORE,
                LocalDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC)
        );
        when(currentActivityIntentRepository.findByUser_Id(userId)).thenReturn(Optional.of(intent));
        MapPlace place = place(1L, TouristCategory.CAFE);

        var result = service().apply(userId, List.of(candidate(place, 0.50d)));

        assertThat(result.intent()).isNull();
        assertThat(result.candidates()).extracting(ScoredCandidate::finalScore).containsExactly(0.50d);
        verify(candidateRepository, never()).findTouristCategoriesByPlaceIds(anyList());
    }

    @Test
    void 만료된_의도는_기존_순위를_유지한다() {
        Long userId = 8L;
        User user = User.builder().id(userId).build();
        UserCurrentActivityIntent intent = UserCurrentActivityIntent.create(
                user,
                CurrentActivityIntent.CAFE,
                LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC)
        );
        when(currentActivityIntentRepository.findByUser_Id(userId)).thenReturn(Optional.of(intent));

        MapPlace food = place(1L, TouristCategory.FOOD);
        MapPlace cafe = place(2L, TouristCategory.CAFE);
        var result = service().apply(userId, List.of(candidate(food, 0.60d), candidate(cafe, 0.50d)));

        assertThat(result.intent()).isNull();
        assertThat(result.candidates()).extracting(candidate -> candidate.place().getId())
                .containsExactly(food.getId(), cafe.getId());
    }

    private CurrentActivityIntentRankingService service() {
        return new CurrentActivityIntentRankingService(
                currentActivityIntentRepository,
                candidateRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private MapPlaceRecommendationCandidateRepository.PlaceTouristCategoryRow row(
            Long placeId,
            TouristCategory category
    ) {
        return new MapPlaceRecommendationCandidateRepository.PlaceTouristCategoryRow() {
            @Override
            public Long getPlaceId() {
                return placeId;
            }

            @Override
            public TouristCategory getCategory() {
                return category;
            }
        };
    }

    private MapPlace place(Long id, TouristCategory category) {
        return MapPlace.builder()
                .id(id)
                .name(category.name())
                .touristCategories(Set.of(category))
                .build();
    }

    private ScoredCandidate candidate(MapPlace place, double score) {
        return new ScoredCandidate(
                place,
                100d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                PlaceRecommendationTrustScoreLoader.NEUTRAL_TRUST_SCORE,
                PersonalSignalType.NONE,
                score,
                PlaceRecommendationCandidateSource.FALLBACK
        );
    }
}
