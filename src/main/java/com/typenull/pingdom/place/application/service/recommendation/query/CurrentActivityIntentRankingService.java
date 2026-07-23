package com.typenull.pingdom.place.application.service.recommendation.query;

import com.typenull.pingdom.identity.domain.repository.UserCurrentActivityIntentRepository;
import com.typenull.pingdom.identity.domain.travel.CurrentActivityIntent;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRecommendationCandidateRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class CurrentActivityIntentRankingService {

    private static final double INTENT_MATCH_BOOST = 0.15d;
    private static final Map<CurrentActivityIntent, Set<TouristCategory>> MATCHING_CATEGORIES = Map.of(
            CurrentActivityIntent.EAT, Set.of(TouristCategory.FOOD),
            CurrentActivityIntent.CAFE, Set.of(TouristCategory.CAFE),
            CurrentActivityIntent.SHOP, Set.of(
                    TouristCategory.BEAUTY,
                    TouristCategory.FASHION,
                    TouristCategory.POP_UP
            ),
            CurrentActivityIntent.ATTEND_EVENT, Set.of(
                    TouristCategory.K_POP,
                    TouristCategory.POP_UP,
                    TouristCategory.EXHIBITION
            ),
            CurrentActivityIntent.NIGHTLIFE, Set.of(TouristCategory.NIGHTLIFE)
    );

    private final UserCurrentActivityIntentRepository currentActivityIntentRepository;
    private final MapPlaceRecommendationCandidateRepository candidateRepository;
    private final Clock clock;

    IntentRankingResult apply(Long userId, List<ScoredCandidate> candidates) {
        CurrentActivityIntent intent = resolveActiveIntent(userId);
        if (intent == null || candidates.isEmpty()) {
            return new IntentRankingResult(intent, candidates);
        }
        Set<TouristCategory> matchingCategories = MATCHING_CATEGORIES.get(intent);
        if (matchingCategories == null) {
            return new IntentRankingResult(null, candidates);
        }

        Map<Long, Set<TouristCategory>> categoriesByPlaceId = loadCategories(candidates);

        List<ScoredCandidate> rankedCandidates = candidates.stream()
                .map(candidate -> applyBoost(candidate, matchingCategories, categoriesByPlaceId))
                .sorted(Comparator.comparingDouble(ScoredCandidate::finalScore).reversed())
                .toList();
        return new IntentRankingResult(intent, rankedCandidates);
    }

    private CurrentActivityIntent resolveActiveIntent(Long userId) {
        if (userId == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return currentActivityIntentRepository.findByUser_Id(userId)
                .filter(activityIntent -> activityIntent.isActiveAt(now))
                .map(activityIntent -> activityIntent.getIntent())
                .orElse(null);
    }

    private ScoredCandidate applyBoost(
            ScoredCandidate candidate,
            Set<TouristCategory> matchingCategories,
            Map<Long, Set<TouristCategory>> categoriesByPlaceId
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
                candidate.dominantSignalType(),
                candidate.finalScore() + INTENT_MATCH_BOOST,
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

    record IntentRankingResult(CurrentActivityIntent intent, List<ScoredCandidate> candidates) {
    }
}
