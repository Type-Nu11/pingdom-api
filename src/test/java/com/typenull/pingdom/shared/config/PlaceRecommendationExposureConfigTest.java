package com.typenull.pingdom.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationExposureService;
import com.typenull.pingdom.place.event.PlaceRecommendationExposureEventListener;
import com.typenull.pingdom.place.event.PlaceRecommendationExposureRecordRequestedEvent;
import com.typenull.pingdom.place.support.PlaceRecommendationExposureProperties;
import com.typenull.pingdom.shared.observability.RecommendationMetrics;
import com.typenull.pingdom.shared.outbox.application.OutboxProperties;
import com.typenull.pingdom.shared.outbox.config.OutboxConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class PlaceRecommendationExposureConfigTest {

    @Test
    void capsWorkersAndQueueAndRejectsInsteadOfRunningOnCaller() throws InterruptedException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ThreadPoolTaskExecutor executor = executor(registry);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean ranOnCaller = new AtomicBoolean();
        try {
            executor.execute(() -> block(started, release));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            executor.execute(() -> {});

            assertThatThrownBy(() -> executor.execute(() -> ranOnCaller.set(true)))
                    .isInstanceOf(TaskRejectedException.class);
            assertThat(ranOnCaller).isFalse();
            assertThat(executor.getCorePoolSize()).isEqualTo(1);
            assertThat(executor.getMaxPoolSize()).isEqualTo(1);
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
            assertThat(registry.get("pingdom.recommendation.exposure.executor.active").gauge().value())
                    .isEqualTo(1d);
            assertThat(registry.get("pingdom.recommendation.exposure.executor.queued").gauge().value())
                    .isEqualTo(1d);
        } finally {
            release.countDown();
            executor.shutdown();
            registry.close();
        }
        assertThatThrownBy(() -> executor.execute(() -> {})).isInstanceOf(TaskRejectedException.class);
    }

    @Test
    void exposureAndOutboxProgressIndependentlyInBothDirections() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ThreadPoolTaskExecutor exposure = executor(registry);
        ThreadPoolTaskExecutor outbox = new OutboxConfig().outboxExecutor(outboxProperties());
        CountDownLatch outboxStarted = new CountDownLatch(1);
        CountDownLatch releaseOutbox = new CountDownLatch(1);
        CountDownLatch exposureStarted = new CountDownLatch(1);
        CountDownLatch releaseExposure = new CountDownLatch(1);
        try {
            var blockedOutbox = outbox.submit(() -> block(outboxStarted, releaseOutbox));
            assertThat(outboxStarted.await(5, TimeUnit.SECONDS)).isTrue();
            var queuedOutbox = outbox.submit(() -> {});
            assertThat(exposure.submit(() -> "exposure-progress").get(5, TimeUnit.SECONDS))
                    .isEqualTo("exposure-progress");

            releaseOutbox.countDown();
            blockedOutbox.get(5, TimeUnit.SECONDS);
            queuedOutbox.get(5, TimeUnit.SECONDS);
            exposure.execute(() -> block(exposureStarted, releaseExposure));
            assertThat(exposureStarted.await(5, TimeUnit.SECONDS)).isTrue();
            exposure.execute(() -> {});
            assertThat(outbox.submit(() -> "outbox-progress").get(5, TimeUnit.SECONDS))
                    .isEqualTo("outbox-progress");
        } finally {
            releaseOutbox.countDown();
            releaseExposure.countDown();
            exposure.shutdown();
            outbox.shutdown();
            registry.close();
        }
    }

    @Test
    void explicitlyWiresExposureExecutorWhenOtherPoolsExist() {
        AtomicReference<String> workerThread = new AtomicReference<>();
        CountDownLatch stored = new CountDownLatch(1);
        var service = mock(PlaceRecommendationExposureService.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            workerThread.set(Thread.currentThread().getName());
            stored.countDown();
            return null;
        }).when(service).recordExposures(7L, 35.18d, 128.107d,
                "request", List.of(101L), "place-rec-v1");

        contextRunner(service).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(ThreadPoolTaskExecutor.class)).hasSize(2);
            context.getBean(PlaceRecommendationExposureEventListener.class).handle(
                    new PlaceRecommendationExposureRecordRequestedEvent(
                            7L, 35.18d, 128.107d, "request", List.of(101L), "place-rec-v1"));
            assertThat(stored.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(workerThread.get()).startsWith("recommendation-exposure-");
            assertThat(context.getBean(PlaceRecommendationExposureProperties.class).workerConcurrency())
                    .isEqualTo(1);
            assertThat(context.getBean(PlaceRecommendationExposureProperties.class).queueCapacity())
                    .isEqualTo(40);
        });
    }

    @Test
    void rejectsInvalidPoolConfiguration() {
        contextRunner(mock(PlaceRecommendationExposureService.class))
                .withPropertyValues("place.recommendation.exposure.worker-concurrency=0")
                .run(context -> assertThat(context).hasFailed());
        contextRunner(mock(PlaceRecommendationExposureService.class))
                .withPropertyValues("place.recommendation.exposure.queue-capacity=-1")
                .run(context -> assertThat(context).hasFailed());
        contextRunner(mock(PlaceRecommendationExposureService.class))
                .withPropertyValues("place.recommendation.exposure.shutdown-await-seconds=0")
                .run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner contextRunner(PlaceRecommendationExposureService service) {
        return new ApplicationContextRunner()
                .withUserConfiguration(ExecutorFixture.class)
                .withBean(OutboxProperties.class, PlaceRecommendationExposureConfigTest::outboxProperties)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean(RecommendationMetrics.class, () -> mock(RecommendationMetrics.class))
                .withBean(PlaceRecommendationExposureService.class, () -> service);
    }

    private ThreadPoolTaskExecutor executor(SimpleMeterRegistry registry) {
        return new PlaceRecommendationExposureConfig().placeRecommendationExposureExecutor(
                new PlaceRecommendationExposureProperties(1, 1, 1), registry);
    }

    private static OutboxProperties outboxProperties() {
        return new OutboxProperties(20, 1, 1, 100, 5,
                Duration.ofSeconds(10), Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofDays(7));
    }

    private static void block(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        try {
            release.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PlaceRecommendationExposureProperties.class)
    @Import({PlaceRecommendationExposureConfig.class, PlaceRecommendationExposureEventListener.class,
            OutboxConfig.class})
    static class ExecutorFixture {
    }
}
