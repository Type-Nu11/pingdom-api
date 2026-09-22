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

    /**
     * 활성 CAFE 의도와 일치한 후보에 0.15 맥락 가점을 더해 기본 점수가 높던 음식 후보보다 앞서는지 확인합니다.
     */
    @Test
    void boostsActiveIntentMatch() {
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

        var result = service.apply(userId, List.of(candidate(food, 0.60d), candidate(cafe, 0.50d)), 0.15d);

        assertThat(result.intent()).isEqualTo(CurrentActivityIntent.CAFE);
        assertThat(result.candidates()).extracting(candidate -> candidate.place().getId())
                .containsExactly(cafe.getId(), food.getId());
        assertThat(result.candidates().getFirst().contextScore()).isEqualTo(0.15d);
    }

    /**
     * EXPLORE 의도는 적용 의도를 null로 두고 점수를 유지하며 카테고리 조회를 생략하는지 확인합니다.
     */
    @Test
    void skipsExploreIntentBoost() {
        Long userId = 9L;
        User user = User.builder().id(userId).build();
        UserCurrentActivityIntent intent = UserCurrentActivityIntent.create(
                user,
                CurrentActivityIntent.EXPLORE,
                LocalDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC)
        );
        when(currentActivityIntentRepository.findByUser_Id(userId)).thenReturn(Optional.of(intent));
        MapPlace place = place(1L, TouristCategory.CAFE);

        var result = service().apply(userId, List.of(candidate(place, 0.50d)), 0.15d);

        assertThat(result.intent()).isNull();
        assertThat(result.candidates()).extracting(ScoredCandidate::finalScore).containsExactly(0.50d);
        verify(candidateRepository, never()).findTouristCategoriesByPlaceIds(anyList());
    }

    /**
     * 1초 전에 만료된 활동 의도는 가점이나 순위 변경에 사용하지 않는지 확인합니다.
     */
    @Test
    void ignoresExpiredActivityIntent() {
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
        var result = service().apply(userId, List.of(candidate(food, 0.60d), candidate(cafe, 0.50d)), 0.15d);

        assertThat(result.intent()).isNull();
        assertThat(result.candidates()).extracting(candidate -> candidate.place().getId())
                .containsExactly(food.getId(), cafe.getId());
    }

    /**
     * 활동 의도 만료 판단용 고정 시계와 모의 저장소를 주입합니다.
     */
    private CurrentActivityIntentRankingService service() {
        return new CurrentActivityIntentRankingService(
                currentActivityIntentRepository,
                candidateRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    /**
     * 지정 장소와 관광 카테고리의 projection을 만듭니다.
     */
    private MapPlaceRecommendationCandidateRepository.PlaceTouristCategoryRow row(
            Long placeId,
            TouristCategory category
    ) {
        return new MapPlaceRecommendationCandidateRepository.PlaceTouristCategoryRow() {
            /** 현재 활동 의도와 비교할 후보 장소 ID를 반환한다. */
            @Override
            public Long getPlaceId() {
                return placeId;
            }

            /** 의도의 선호 카테고리와 대조할 장소 카테고리를 반환한다. */
            @Override
            public TouristCategory getCategory() {
                return category;
            }
        };
    }

    /**
     * 관광 카테고리가 지정된 순위 비교용 장소를 만듭니다.
     */
    private MapPlace place(Long id, TouristCategory category) {
        return MapPlace.builder()
                .id(id)
                .name(category.name())
                .touristCategories(Set.of(category))
                .build();
    }

    /**
     * 기본 점수만 다르고 다른 신호는 동일한 후보를 만듭니다.
     */
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
                0d,
                PersonalSignalType.NONE,
                score,
                PlaceRecommendationCandidateSource.FALLBACK
        );
    }
}
