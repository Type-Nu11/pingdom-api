package com.typenull.pingdom.place.application.service.recommendation.policy;

import com.typenull.pingdom.place.domain.recommendation.policy.PlaceRecommendationTrafficPolicy;

import com.typenull.pingdom.place.support.PlaceRecommendationProperties;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.CandidateMix;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RankingWeights;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RecommendationStage;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.VersionPolicy;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationTrafficPolicyRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceRecommendationPolicyServiceTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * 명시한 버전이 없으면 실험 버전 트래픽 배분과 무관하게 기본 정책을 사용하는지 확인.
     */
    @Test
    void defaultsUnknownRequestedVersion() {
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

    /**
     * 알 수 없는 요청 버전과 비활성 기본 정책 조합에서 설정된 활성 폴백 버전을 선택하고 원래 요청 버전을 보존하는지 확인.
     */
    @Test
    void fallsBackFromDisabledDefault() {
        PlaceRecommendationProperties properties = new PlaceRecommendationProperties(
                "place-rec-v1",
                List.of(
                        createPolicy("place-rec-v1", RecommendationStage.STABLE, 100),
                        createPolicy("place-rec-v2", RecommendationStage.EXPERIMENTAL, 0)
                )
        );
        PlaceRecommendationPolicyRepositoryContext context = createContext(properties);
        Mockito.when(context.repository().findAll()).thenReturn(List.of(
                com.typenull.pingdom.place.domain.recommendation.policy.PlaceRecommendationTrafficPolicy.create(
                        "place-rec-v1",
                        100,
                        false,
                        "place-rec-v2"
                ),
                com.typenull.pingdom.place.domain.recommendation.policy.PlaceRecommendationTrafficPolicy.create(
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

    /**
     * DB에 저장한 0/100 트래픽 배분이 기본 설정을 덮어써 사용자 버킷을 실험 버전으로 보내는지 확인.
     */
    @Test
    void appliesStoredTrafficOverrides() {
        PlaceRecommendationProperties properties = new PlaceRecommendationProperties(
                "place-rec-v1",
                List.of(
                        createPolicy("place-rec-v1", RecommendationStage.STABLE, 100),
                        createPolicy("place-rec-v2", RecommendationStage.EXPERIMENTAL, 0)
                )
        );
        PlaceRecommendationPolicyRepositoryContext context = createContext(properties);
        Mockito.when(context.repository().findAll()).thenReturn(List.of(
                com.typenull.pingdom.place.domain.recommendation.policy.PlaceRecommendationTrafficPolicy.create(
                        "place-rec-v1",
                        0,
                        true,
                        null
                ),
                com.typenull.pingdom.place.domain.recommendation.policy.PlaceRecommendationTrafficPolicy.create(
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

    /**
     * 명시한 실험 버전이 비활성화되면 설정된 기본 버전으로 이동하고 sourceVersion을 유지하는지 확인.
     */
    @Test
    void fallsBackFromDisabledVersion() {
        PlaceRecommendationProperties properties = new PlaceRecommendationProperties(
                "place-rec-v1",
                List.of(
                        createPolicy("place-rec-v1", RecommendationStage.STABLE, 100),
                        createPolicy("place-rec-v2", RecommendationStage.EXPERIMENTAL, 0)
                )
        );
        PlaceRecommendationPolicyRepositoryContext context = createContext(properties);
        Mockito.when(context.repository().findAll()).thenReturn(List.of(
                com.typenull.pingdom.place.domain.recommendation.policy.PlaceRecommendationTrafficPolicy.create(
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

    /**
     * 정책 갱신 시 잠금 저장소 메서드를 호출하는지 확인. 모의 테스트로 실제 DB 잠금 획득은 검증 범위에서 제외.
     */
    @Test
    void loadsLockedTrafficPolicies() {
        PlaceRecommendationProperties properties = new PlaceRecommendationProperties(
                "place-rec-v1",
                List.of(createPolicy("place-rec-v1", RecommendationStage.STABLE, 100))
        );
        PlaceRecommendationTrafficPolicyRepository repository = Mockito.mock(PlaceRecommendationTrafficPolicyRepository.class);
        Mockito.when(repository.findAll()).thenReturn(List.of());
        Mockito.when(repository.findAllForUpdate()).thenReturn(List.of());
        PlaceRecommendationPolicyService service = new PlaceRecommendationPolicyService(properties, repository);
        service.initialize();

        service.updateTrafficPolicies(Map.of(
                "place-rec-v1",
                new PlaceRecommendationPolicyService.PolicyUpdateCommand(100, true, null)
        ));

        Mockito.verify(repository).findAllForUpdate();
    }

    /**
     * 저장 정책이 없는 저장소와 설정 기반 서비스 조합을 생성.
     */
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

    /**
     * 후보 mix와 익명 가중치 누락이 각각 중첩 Bean Validation 위반으로 보고되는지 확인.
     */
    @Test
    void rejectsMissingNestedPolicy() {
        PlaceRecommendationProperties properties = new PlaceRecommendationProperties(
                "place-rec-v1",
                List.of(new VersionPolicy(
                        "place-rec-v1",
                        RecommendationStage.STABLE,
                        100,
                        false,
                        4,
                        0.75d,
                        0.10d,
                        0.15d,
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

    /**
     * 버전·단계·트래픽을 지정하고 공통 가중치를 갖춘 정책 fixture를 생성.
     */
    private VersionPolicy createPolicy(String version, RecommendationStage stage, int trafficPercentage) {
        return new VersionPolicy(
                version,
                stage,
                trafficPercentage,
                stage == RecommendationStage.EXPERIMENTAL,
                4,
                0.75d,
                0.10d,
                0.15d,
                new CandidateMix(0.35d, 0.25d, 0.20d, 0.20d),
                createWeights(),
                createWeights()
        );
    }

    /**
     * 신뢰 가중치가 0인 레거시 추천 가중치 fixture를 반환.
     */
    private RankingWeights createWeights() {
        return new RankingWeights(0.33d, 0.30d, 0.13d, 0.07d, 0.07d, 0.08d, 0.06d, 0.0d);
    }
}
