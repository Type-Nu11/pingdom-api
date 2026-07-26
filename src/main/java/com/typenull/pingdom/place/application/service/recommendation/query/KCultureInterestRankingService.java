package com.typenull.pingdom.place.application.service.recommendation.query;

import com.typenull.pingdom.identity.domain.TravelPurpose;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRecommendationCandidateRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class KCultureInterestRankingService {

    private static final Map<TravelPurpose, TouristCategory> INTEREST_CATEGORY_MAPPING = Map.of(
            TravelPurpose.K_POP, TouristCategory.K_POP,
            TravelPurpose.BEAUTY, TouristCategory.BEAUTY,
            TravelPurpose.FASHION, TouristCategory.FASHION,
            TravelPurpose.CAFE, TouristCategory.CAFE,
            TravelPurpose.FOOD, TouristCategory.FOOD,
            TravelPurpose.POP_UP, TouristCategory.POP_UP,
            TravelPurpose.EXHIBITION, TouristCategory.EXHIBITION,
            TravelPurpose.NIGHTLIFE, TouristCategory.NIGHTLIFE
    );

    private final UserRepository userRepository;
    private final MapPlaceRecommendationCandidateRepository candidateRepository;

    InterestRankingResult apply(Long userId, List<ScoredCandidate> candidates, double interestMatchBoost) {
        if (candidates.isEmpty() || interestMatchBoost <= 0d) {
            return new InterestRankingResult(Set.of(), candidates, null);
        }
        Set<TravelPurpose> interests = resolveInterests(userId);
        if (interests.isEmpty()) {
            return new InterestRankingResult(Set.of(), candidates, null);
        }

        Set<TouristCategory> matchingCategories = interests.stream()
                .map(INTEREST_CATEGORY_MAPPING::get)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (matchingCategories.isEmpty()) {
            return new InterestRankingResult(Set.of(), candidates, null);
        }

        Map<Long, Set<TouristCategory>> categoriesByPlaceId = loadCategories(candidates);
        Set<TouristCategory> candidateCategories = categoriesByPlaceId.values().stream()
                .flatMap(Set::stream)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<TravelPurpose> appliedInterests = interests.stream()
                .filter(interest -> candidateCategories.contains(INTEREST_CATEGORY_MAPPING.get(interest)))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<ScoredCandidate> rankedCandidates = candidates.stream()
                .map(candidate -> applyBoost(candidate, matchingCategories, categoriesByPlaceId, interestMatchBoost))
                .sorted(java.util.Comparator.comparingDouble(ScoredCandidate::finalScore).reversed())
                .toList();
        return new InterestRankingResult(appliedInterests, rankedCandidates, Map.copyOf(categoriesByPlaceId));
    }

    private Set<TravelPurpose> resolveInterests(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return userRepository.findById(userId)
                .map(user -> user.currentTravelPurposes().stream()
                        .filter(interest -> interest != TravelPurpose.OTHER)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()))
                .orElseGet(Set::of);
    }

    private ScoredCandidate applyBoost(
            ScoredCandidate candidate,
            Set<TouristCategory> matchingCategories,
            Map<Long, Set<TouristCategory>> categoriesByPlaceId,
            double interestMatchBoost
    ) {
        boolean matches = categoriesByPlaceId.getOrDefault(candidate.place().getId(), Set.of())
                .stream()
                .anyMatch(matchingCategories::contains);
        if (!matches) {
            return candidate;
        }
        return new ScoredCandidate(
                candidate.place(),
                candidate.distanceMeters(),
                candidate.geoScore(),
                candidate.personalScore(),
                candidate.qualityScore(),
                candidate.engagementScore(),
                candidate.conversionScore(),
                candidate.explorationScore(),
                candidate.freshnessScore(),
                candidate.trustScore(),
                candidate.contextScore() + interestMatchBoost,
                candidate.benefitScore(),
                candidate.availabilityScore(),
                candidate.boostScore(),
                candidate.dominantSignalType(),
                candidate.finalScore() + interestMatchBoost,
                candidate.candidateSource()
        );
    }

    private Map<Long, Set<TouristCategory>> loadCategories(List<ScoredCandidate> candidates) {
        List<Long> placeIds = candidates.stream()
                .map(candidate -> candidate.place().getId())
                .distinct()
                .toList();
        Map<Long, Set<TouristCategory>> categoriesByPlaceId = new HashMap<>();
        for (MapPlaceRecommendationCandidateRepository.PlaceTouristCategoryRow row
                : candidateRepository.findTouristCategoriesByPlaceIds(placeIds)) {
            categoriesByPlaceId.computeIfAbsent(row.getPlaceId(), ignored -> new HashSet<>())
                    .add(row.getCategory());
        }
        return categoriesByPlaceId;
    }

    record InterestRankingResult(
            Set<TravelPurpose> interests,
            List<ScoredCandidate> candidates,
            Map<Long, Set<TouristCategory>> categoriesByPlaceId
    ) {
    }
}
