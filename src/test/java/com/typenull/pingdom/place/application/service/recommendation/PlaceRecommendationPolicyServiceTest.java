package com.typenull.pingdom.place.application.service.recommendation;

import com.typenull.pingdom.place.support.PlaceRecommendationProperties;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.CandidateMix;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RankingWeights;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RecommendationStage;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.VersionPolicy;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationTrafficPolicyRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
        PlaceRecommendationPolicyRepositoryContext context = createContext(properties);
        PlaceRecommendationPolicyService service = context.service();
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
    void invalidRequestedVersionFallsBackToFirstEnabledVersionWhenDefaultDisabled() {
        PlaceRecommendationProperties properties = new PlaceRecommendationProperties(
                "place-rec-v1",
                List.of(
                        createPolicy("place-rec-v1", RecommendationStage.STABLE, 100),
                        createPolicy("place-rec-v2", RecommendationStage.EXPERIMENTAL, 0)
                )
        );
        PlaceRecommendationPolicyRepositoryContext context = createContext(properties);
        Mockito.when(context.repository().findAll()).thenReturn(List.of(
                com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationTrafficPolicy.create(
                        "place-rec-v1",
                        100,
                        false,
                        "place-rec-v2"
                ),
                com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationTrafficPolicy.create(
                        "place-rec-v2",
                        0,
                        true,
                        null
                )
        ));
        PlaceRecommendationPolicyService service = context.service();
        service.initialize();

        PlaceRecommendationPolicyService.ResolvedRecommendationPolicy policy = service.resolve(
                99L,
                35.1801d,
                128.1078d,
                "unknown-version"
        );

        assertEquals("place-rec-v2", policy.version());
        assertEquals("unknown-version", policy.sourceVersion());
    }

    @Test
    void overrideTrafficPercentageChangesBucketResolution() {
        PlaceRecommendationProperties properties = new PlaceRecommendationProperties(
                "place-rec-v1",
                List.of(
                        createPolicy("place-rec-v1", RecommendationStage.STABLE, 100),
                        createPolicy("place-rec-v2", RecommendationStage.EXPERIMENTAL, 0)
                )
        );
        PlaceRecommendationPolicyRepositoryContext context = createContext(properties);
        Mockito.when(context.repository().findAll()).thenReturn(List.of(
                com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationTrafficPolicy.create(
                        "place-rec-v1",
                        0,
                        true,
                        null
                ),
                com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationTrafficPolicy.create(
                        "place-rec-v2",
                        100,
                        true,
                        null
                )
        ));
        PlaceRecommendationPolicyService service = context.service();
        service.initialize();

        PlaceRecommendationPolicyService.ResolvedRecommendationPolicy policy = service.resolve(
                1L,
                35.1801d,
                128.1078d,
                null
        );

        assertEquals("place-rec-v2", policy.version());
    }

    @Test
    void disabledRequestedVersionFallsBackToConfiguredVersion() {
        PlaceRecommendationProperties properties = new PlaceRecommendationProperties(
                "place-rec-v1",
                List.of(
                        createPolicy("place-rec-v1", RecommendationStage.STABLE, 100),
                        createPolicy("place-rec-v2", RecommendationStage.EXPERIMENTAL, 0)
                )
        );
        PlaceRecommendationPolicyRepositoryContext context = createContext(properties);
        Mockito.when(context.repository().findAll()).thenReturn(List.of(
                com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationTrafficPolicy.create(
                        "place-rec-v2",
                        0,
                        false,
                        "place-rec-v1"
                )
        ));
        PlaceRecommendationPolicyService service = context.service();
        service.initialize();

        PlaceRecommendationPolicyService.ResolvedRecommendationPolicy policy = service.resolve(
                1L,
                35.1801d,
                128.1078d,
                "place-rec-v2"
        );

        assertEquals("place-rec-v1", policy.version());
        assertEquals("place-rec-v2", policy.sourceVersion());
    }

    private PlaceRecommendationPolicyRepositoryContext createContext(PlaceRecommendationProperties properties) {
        PlaceRecommendationTrafficPolicyRepository repository = Mockito.mock(PlaceRecommendationTrafficPolicyRepository.class);
        Mockito.when(repository.findAll()).thenReturn(List.of());
        return new PlaceRecommendationPolicyRepositoryContext(
                repository,
                new PlaceRecommendationPolicyService(properties, repository)
        );
    }

    private record PlaceRecommendationPolicyRepositoryContext(
            PlaceRecommendationTrafficPolicyRepository repository,
            PlaceRecommendationPolicyService service
    ) {
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
