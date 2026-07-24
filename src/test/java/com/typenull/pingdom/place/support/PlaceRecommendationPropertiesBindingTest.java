package com.typenull.pingdom.place.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PlaceRecommendationPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "place.recommendation.default-version=place-rec-v1",
                    "place.recommendation.versions[0].version=place-rec-v1",
                    "place.recommendation.versions[0].stage=STABLE",
                    "place.recommendation.versions[0].traffic-percentage=100",
                    "place.recommendation.versions[0].feature-logging-enabled=false",
                    "place.recommendation.versions[0].portfolio-size-multiplier=4",
                    "place.recommendation.versions[0].mmr-relevance-weight=0.75",
                    "place.recommendation.versions[0].interest-match-boost=0.10",
                    "place.recommendation.versions[0].intent-match-boost=0.15",
                    "place.recommendation.versions[0].benefit-boost=0.05",
                    "place.recommendation.versions[0].availability-boost=0.05",
                    "place.recommendation.versions[0].mix.personal-ratio=0.35",
                    "place.recommendation.versions[0].mix.popular-ratio=0.25",
                    "place.recommendation.versions[0].mix.fresh-ratio=0.20",
                    "place.recommendation.versions[0].mix.geo-ratio=0.20",
                    "place.recommendation.versions[0].personalized-weights.geo-weight=0.33",
                    "place.recommendation.versions[0].personalized-weights.personal-weight=0.30",
                    "place.recommendation.versions[0].personalized-weights.quality-weight=0.13",
                    "place.recommendation.versions[0].personalized-weights.engagement-weight=0.07",
                    "place.recommendation.versions[0].personalized-weights.conversion-weight=0.07",
                    "place.recommendation.versions[0].personalized-weights.freshness-weight=0.08",
                    "place.recommendation.versions[0].personalized-weights.exploration-weight=0.06",
                    "place.recommendation.versions[0].personalized-weights.trust-weight=0.00",
                    "place.recommendation.versions[0].anonymous-weights.geo-weight=0.48",
                    "place.recommendation.versions[0].anonymous-weights.personal-weight=0.00",
                    "place.recommendation.versions[0].anonymous-weights.quality-weight=0.16",
                    "place.recommendation.versions[0].anonymous-weights.engagement-weight=0.10",
                    "place.recommendation.versions[0].anonymous-weights.conversion-weight=0.08",
                    "place.recommendation.versions[0].anonymous-weights.freshness-weight=0.12",
                    "place.recommendation.versions[0].anonymous-weights.exploration-weight=0.09",
                    "place.recommendation.versions[0].anonymous-weights.trust-weight=0.00"
            );

    @Test
    void 추천_버전_설정을_canonical_constructor로_바인딩한다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            PlaceRecommendationProperties properties = context.getBean(PlaceRecommendationProperties.class);
            assertThat(properties.defaultVersion()).isEqualTo("place-rec-v1");
            assertThat(properties.versions()).hasSize(1);
            assertThat(properties.versions().getFirst().benefitBoost()).isEqualTo(0.05d);
            assertThat(properties.versions().getFirst().availabilityBoost()).isEqualTo(0.05d);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PlaceRecommendationProperties.class)
    static class TestConfiguration {
    }
}
