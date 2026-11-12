package com.typenull.pingdom.shared.config;

import com.typenull.pingdom.place.support.PlaceRecommendationExposureProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class PlaceRecommendationExposureConfig {

    @Bean(name = "placeRecommendationExposureExecutor")
    public ThreadPoolTaskExecutor placeRecommendationExposureExecutor(
            PlaceRecommendationExposureProperties properties,
            MeterRegistry meterRegistry
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.workerConcurrency());
        executor.setMaxPoolSize(properties.workerConcurrency());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("recommendation-exposure-");
        // 포화 시 추천 요청 스레드에서 저장하지 않고 listener가 확인 가능한 거절로 처리합니다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(properties.shutdownAwaitSeconds());
        executor.initialize();

        Gauge.builder("pingdom.recommendation.exposure.executor.active", executor,
                        ThreadPoolTaskExecutor::getActiveCount)
                .register(meterRegistry);
        Gauge.builder("pingdom.recommendation.exposure.executor.queued", executor,
                        ThreadPoolTaskExecutor::getQueueSize)
                .register(meterRegistry);
        return executor;
    }
}
