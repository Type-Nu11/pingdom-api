package com.typenull.pingdom.place.application.service.recommendation.query;

import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class PlaceRecommendationUserSignalLoader {

    private static final int MAX_PERSONAL_SEED_COUNT = 64;

    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapImageLikeRepository mapImageLikeRepository;
    private final MapImageRepository mapImageRepository;

    UserSignalContext loadUserSignals(Long userId) {
        if (userId == null) {
            return UserSignalContext.empty();
        }

        Map<Long, Double> seedWeights = new HashMap<>();
        Map<Long, PersonalSignalType> signalTypes = new HashMap<>();

        registerSignals(
                mapBookmarkRepository.findPlaceIdsByUserId(userId),
                1.0d,
                PersonalSignalType.BOOKMARK,
                seedWeights,
                signalTypes
        );
        registerSignals(
                mapImageLikeRepository.findPlaceIdsByUserId(userId),
                0.6d,
                PersonalSignalType.LIKE,
                seedWeights,
                signalTypes
        );
        registerSignals(
                mapImageRepository.findPlaceIdsByUserId(userId),
                0.3d,
                PersonalSignalType.UPLOAD,
                seedWeights,
                signalTypes
        );

        Map<Long, Double> limitedSeedWeights = seedWeights.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(MAX_PERSONAL_SEED_COUNT)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, ignored) -> left, LinkedHashMap::new));
        Map<Long, PersonalSignalType> limitedSignalTypes = limitedSeedWeights.keySet().stream()
                .collect(Collectors.toMap(key -> key, signalTypes::get));
        return new UserSignalContext(limitedSeedWeights, limitedSignalTypes, java.util.Set.copyOf(limitedSeedWeights.keySet()));
    }

    private void registerSignals(
            Collection<Long> placeIds,
            double weight,
            PersonalSignalType signalType,
            Map<Long, Double> seedWeights,
            Map<Long, PersonalSignalType> signalTypes
    ) {
        for (Long placeId : placeIds) {
            seedWeights.merge(placeId, weight, Double::sum);
            signalTypes.merge(placeId, signalType, PersonalSignalType::stronger);
        }
    }
}
