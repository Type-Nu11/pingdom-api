package com.typenull.pingdom.place.application.service.recommendation.policy;

import com.typenull.pingdom.place.support.PlaceRecommendationProperties;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.CandidateMix;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RankingWeights;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RecommendationStage;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.VersionPolicy;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceRecommendationPolicyServiceTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void invalidRequestedVersionFallsBackToDefaultVersion() {
        PlaceRecommendationProperties properties = new PlaceRecommendationProperties(
                "place-rec-v1",
                List.of(
                        createPolicy("place-rec-v1", RecommendationStage.STABLE, 0),
                        createPolicy("place-rec-v2", RecommendationStage.EXPERIMENTAL, 100)
                )
        );
        PlaceRecommendationPolicyService service = new PlaceRecommendationPolicyService(properties);
        service.initialize();

        PlaceRecommendationPolicyService.ResolvedRecommendationPolicy policy = service.resolve(
                99L,
                35.1801d,
                128.1078d,
                "unknown-version"
        );

        assertEquals("place-rec-v1", policy.version());
    }

    @Test
    void missingNestedPolicyPropertiesFailValidation() {
        PlaceRecommendationProperties properties = new PlaceRecommendationProperties(
                "place-rec-v1",
                List.of(new VersionPolicy(
                        "place-rec-v1",
                        RecommendationStage.STABLE,
                        100,
                        false,
                        4,
                        0.75d,
                        null,
                        createWeights(),
                        null
                ))
        );

        var violations = validator.validate(properties);

        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(violation -> violation.getPropertyPath().toString()
                .equals("versions[0].mix")));
        assertTrue(violations.stream().anyMatch(violation -> violation.getPropertyPath().toString()
                .equals("versions[0].anonymousWeights")));
    }

    private VersionPolicy createPolicy(String version, RecommendationStage stage, int trafficPercentage) {
        return new VersionPolicy(
                version,
                stage,
                trafficPercentage,
                stage == RecommendationStage.EXPERIMENTAL,
                4,
                0.75d,
                new CandidateMix(0.35d, 0.25d, 0.20d, 0.20d),
                createWeights(),
                createWeights()
        );
    }

    private RankingWeights createWeights() {
        return new RankingWeights(0.33d, 0.30d, 0.13d, 0.07d, 0.07d, 0.08d, 0.06d);
    }
}
