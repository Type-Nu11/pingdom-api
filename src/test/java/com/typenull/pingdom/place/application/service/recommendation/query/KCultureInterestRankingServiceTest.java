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

    /**
     * K_POP 관심사와 일치한 후보에 0.10을 더해 카페보다 우선하고 적용 관심사에 포함하는지 확인.
     */
    @Test
    void boostsMatchingInterest() {
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

    /**
     * OTHER만 선택하면 점수를 유지하고 카테고리 조회를 생략하는지 확인.
     */
    @Test
    void ignoresOtherOnlyInterest() {
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

    /**
     * 후보에 해당 카테고리가 없으면 관심사를 적용 목록에 넣지 않고 점수를 유지하는지 확인.
     */
    @Test
    void excludesUnmatchedAppliedInterest() {
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

    /**
     * 가점 설정 0이면 사용자 조회 없이 기존 점수와 빈 적용 관심사를 반환하는지 확인.
     */
    @Test
    void skipsDisabledInterestBoost() {
        MapPlace place = place(1L, TouristCategory.K_POP);

        var result = service().apply(7L, List.of(candidate(place, 0.50d)), 0d);

        assertThat(result.interests()).isEmpty();
        assertThat(result.candidates()).extracting(ScoredCandidate::finalScore).containsExactly(0.50d);
        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    /**
     * 익명 추천은 사용자·카테고리를 조회하지 않고 적용 관심사를 비우는지 확인.
     */
    @Test
    void skipsAnonymousInterestLookup() {
        MapPlace place = place(1L, TouristCategory.K_POP);

        var result = service().apply(null, List.of(candidate(place, 0.50d)), 0.10d);

        assertThat(result.interests()).isEmpty();
        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
        verify(candidateRepository, never()).findTouristCategoriesByPlaceIds(anyList());
    }

    /**
     * 관심사와 카테고리 모의 저장소를 사용하는 서비스를 생성.
     */
    private KCultureInterestRankingService service() {
        return new KCultureInterestRankingService(userRepository, candidateRepository);
    }

    /**
     * 후보 카테고리 일치 판단에 사용할 projection을 생성.
     */
    private MapPlaceRecommendationCandidateRepository.PlaceTouristCategoryRow row(
            Long placeId,
            TouristCategory category
    ) {
        return new MapPlaceRecommendationCandidateRepository.PlaceTouristCategoryRow() {
            /** 관심사 가점을 부여할 후보 장소 ID를 반환. */
            @Override
            public Long getPlaceId() {
                return placeId;
            }

            /** 사용자의 K-컬처 관심사와 비교할 장소 카테고리를 반환. */
            @Override
            public TouristCategory getCategory() {
                return category;
            }
        };
    }

    /**
     * 관광 카테고리가 정해진 관심사 후보를 생성.
     */
    private MapPlace place(Long id, TouristCategory category) {
        return MapPlace.builder()
                .id(id)
                .name(category.name())
                .touristCategories(Set.of(category))
                .build();
    }

    /**
     * 관심사 가점만 관찰할 수 있도록 다른 신호를 고정한 후보를 생성.
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
