package com.typenull.pingdom.place.application.service.recommendation.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.TravelPurpose;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.candidate.PlaceRecommendationCandidateSource;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRecommendationCandidateRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KCultureInterestRankingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MapPlaceRecommendationCandidateRepository candidateRepository;

    @Test
    void 관심사와_일치하는_장소의_순위를_높인다() {
        User user = User.builder()
                .id(7L)
                .travelPurposes(Set.of(TravelPurpose.K_POP))
                .build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        MapPlace cafe = place(1L, TouristCategory.CAFE);
        MapPlace kPop = place(2L, TouristCategory.K_POP);
        when(candidateRepository.findTouristCategoriesByPlaceIds(List.of(cafe.getId(), kPop.getId())))
                .thenReturn(List.of(row(cafe.getId(), TouristCategory.CAFE), row(kPop.getId(), TouristCategory.K_POP)));

        var result = service().apply(7L, List.of(candidate(cafe, 0.55d), candidate(kPop, 0.50d)), 0.10d);

        assertThat(result.interests()).containsExactly(TravelPurpose.K_POP);
        assertThat(result.candidates()).extracting(candidate -> candidate.place().getId())
                .containsExactly(kPop.getId(), cafe.getId());
        assertThat(result.candidates().getFirst().contextScore()).isEqualTo(0.10d);
    }

    @Test
    void OTHER만_선택한_사용자는_기존_순위를_유지한다() {
        User user = User.builder()
                .id(8L)
                .travelPurposes(Set.of(TravelPurpose.OTHER))
                .build();
        when(userRepository.findById(8L)).thenReturn(Optional.of(user));
        MapPlace place = place(1L, TouristCategory.OTHER);

        var result = service().apply(8L, List.of(candidate(place, 0.50d)), 0.10d);

        assertThat(result.interests()).isEmpty();
        assertThat(result.candidates()).extracting(ScoredCandidate::finalScore).containsExactly(0.50d);
        verify(candidateRepository, never()).findTouristCategoriesByPlaceIds(anyList());
    }

    @Test
    void 후보와_일치하지_않는_관심사는_적용_목록에서_제외한다() {
        User user = User.builder()
                .id(9L)
                .travelPurposes(Set.of(TravelPurpose.K_POP))
                .build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        MapPlace cafe = place(1L, TouristCategory.CAFE);
        when(candidateRepository.findTouristCategoriesByPlaceIds(List.of(cafe.getId())))
                .thenReturn(List.of(row(cafe.getId(), TouristCategory.CAFE)));

        var result = service().apply(9L, List.of(candidate(cafe, 0.50d)), 0.10d);

        assertThat(result.interests()).isEmpty();
        assertThat(result.candidates()).extracting(ScoredCandidate::finalScore).containsExactly(0.50d);
    }

    @Test
    void 버전_정책이_비활성화하면_관심사_랭킹을_적용하지_않는다() {
        MapPlace place = place(1L, TouristCategory.K_POP);

        var result = service().apply(7L, List.of(candidate(place, 0.50d)), 0d);

        assertThat(result.interests()).isEmpty();
        assertThat(result.candidates()).extracting(ScoredCandidate::finalScore).containsExactly(0.50d);
        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void 익명_사용자는_사용자와_카테고리를_조회하지_않는다() {
        MapPlace place = place(1L, TouristCategory.K_POP);

        var result = service().apply(null, List.of(candidate(place, 0.50d)), 0.10d);

        assertThat(result.interests()).isEmpty();
        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
        verify(candidateRepository, never()).findTouristCategoriesByPlaceIds(anyList());
    }

    private KCultureInterestRankingService service() {
        return new KCultureInterestRankingService(userRepository, candidateRepository);
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
                0d,
                PersonalSignalType.NONE,
                score,
                PlaceRecommendationCandidateSource.FALLBACK
        );
    }
}
