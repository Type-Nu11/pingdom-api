package com.typenull.pingdom.place.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationExposureService;
import com.typenull.pingdom.shared.observability.RecommendationMetrics;
import com.typenull.pingdom.place.support.PlaceRecommendationExposureProperties;
import com.typenull.pingdom.shared.config.PlaceRecommendationExposureConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
class PlaceRecommendationExposureEventListenerTest {

    @Mock
    private PlaceRecommendationExposureService placeRecommendationExposureService;

    private PlaceRecommendationExposureEventListener eventListener;
    private SimpleMeterRegistry registry;
    private RecommendationMetrics metrics;

    /** executor를 동기 실행으로 바꿔 서비스 위임과 예외 억제를 테스트 호출 안에서 관찰. */
    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new RecommendationMetrics(registry);
        eventListener = new PlaceRecommendationExposureEventListener(
                placeRecommendationExposureService,
                Runnable::run,
                metrics
        );
    }

    @AfterEach
    void closeRegistry() {
        registry.close();
    }

    /** AFTER_COMMIT·fallbackExecution=false 어노테이션과 노출 인자 위임을 확인. 실제 커밋 이벤트 처리는 검증 범위에서 제외. */
    @Test
    @DisplayName("추천 노출 기록은 커밋 후에 별도 처리로 위임한다")
    void handlesExposureAfterCommit() throws NoSuchMethodException {
        Method handleMethod = PlaceRecommendationExposureEventListener.class.getDeclaredMethod(
                "handle",
                PlaceRecommendationExposureRecordRequestedEvent.class
        );
        TransactionalEventListener annotation = handleMethod.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isFalse();

        PlaceRecommendationExposureRecordRequestedEvent event = event();
        eventListener.handle(event);

        verify(placeRecommendationExposureService).recordExposures(
                7L,
                35.1800d,
                128.1070d,
                "recommendation-request-id",
                List.of(101L, 102L),
                "place-rec-v1"
        );
        assertThat(eventCount("persisted")).isEqualTo(1d);
        assertThat(registry.get("pingdom.recommendation.exposure.items")
                .tag("stage", "persisted").counter().count()).isEqualTo(2d);
    }

    /** 기록 서비스가 실패해도 listener 호출자가 예외를 받지 않는지 확인. */
    @Test
    @DisplayName("추천 노출 기록 실패는 이미 성공한 추천 응답 흐름으로 전파하지 않는다")
    void suppressesExposurePersistenceFailure() {
        PlaceRecommendationExposureRecordRequestedEvent event = event();
        doThrow(new IllegalStateException("exposure persistence failed"))
                .when(placeRecommendationExposureService)
                .recordExposures(
                        event.userId(),
                        event.latitude(),
                        event.longitude(),
                        event.requestId(),
                        event.placeIds(),
                        event.recommendationVersion()
                );

        assertThatCode(() -> eventListener.handle(event)).doesNotThrowAnyException();
        assertThat(eventCount("persistence_failed")).isEqualTo(1d);
        assertThat(registry.find("pingdom.recommendation.exposure.events")
                .tag("stage", "persisted").counter()).isNull();
        assertThat(registry.find("pingdom.recommendation.exposure.dropped").counter()).isNull();
    }

    /** executor가 작업 제출을 거부해도 listener가 예외를 밖으로 전파하지 않는지 확인. */
    @Test
    @DisplayName("추천 노출 작업 제출 실패는 추천 응답 흐름으로 전파하지 않는다")
    void suppressesExecutorSubmissionFailure() {
        eventListener = new PlaceRecommendationExposureEventListener(
                placeRecommendationExposureService,
                task -> {
                    throw new RejectedExecutionException("executor is unavailable");
                },
                metrics
        );

        assertThatCode(() -> eventListener.handle(event())).doesNotThrowAnyException();
        verifyNoInteractions(placeRecommendationExposureService);
        assertThat(eventCount("submission_rejected")).isEqualTo(1d);
        assertThat(registry.get("pingdom.recommendation.exposure.dropped")
                .tag("reason", "unknown").counter().count()).isEqualTo(2d);
    }

    @Test
    void recordsSubmissionBeforeWorkStartsAndSuccessOnlyAfterServiceReturns() {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        eventListener = new PlaceRecommendationExposureEventListener(
                placeRecommendationExposureService, queued::set, metrics);

        eventListener.handle(event());

        verifyNoInteractions(placeRecommendationExposureService);
        assertThat(eventCount("received")).isEqualTo(1d);
        assertThat(eventCount("submitted")).isEqualTo(1d);
        assertThat(registry.find("pingdom.recommendation.exposure.events")
                .tag("stage", "persisted").counter()).isNull();

        queued.get().run();

        assertThat(eventCount("started")).isEqualTo(1d);
        assertThat(eventCount("persisted")).isEqualTo(1d);
        assertThat(registry.get("pingdom.recommendation.exposure.queue_wait").timer().count()).isEqualTo(1L);
        assertThat(registry.get("pingdom.recommendation.exposure.processing")
                .tag("result", "success").timer().count()).isEqualTo(1L);
    }

    @Test
    void distinguishesUnexpectedSubmissionFailureFromRejection() {
        eventListener = new PlaceRecommendationExposureEventListener(
                placeRecommendationExposureService,
                task -> {
                    throw new IllegalStateException("executor not initialized");
                },
                metrics
        );

        assertThatCode(() -> eventListener.handle(event())).doesNotThrowAnyException();

        verifyNoInteractions(placeRecommendationExposureService);
        assertThat(eventCount("submission_failed")).isEqualTo(1d);
        assertThat(registry.find("pingdom.recommendation.exposure.events")
                .tag("stage", "submission_rejected").counter()).isNull();
        assertThat(registry.find("pingdom.recommendation.exposure.dropped").counter()).isNull();
    }

    @Test
    void distinguishesCapacityAndShutdownRejectionsWithoutCallingPersistence() throws InterruptedException {
        var executor = new PlaceRecommendationExposureConfig().placeRecommendationExposureExecutor(
                new PlaceRecommendationExposureProperties(1, 0, 1), registry);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        eventListener = new PlaceRecommendationExposureEventListener(
                placeRecommendationExposureService, executor, metrics);
        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatCode(() -> eventListener.handle(event())).doesNotThrowAnyException();
            assertThat(registry.get("pingdom.recommendation.exposure.dropped")
                    .tag("reason", "capacity").counter().count()).isEqualTo(2d);
        } finally {
            release.countDown();
            executor.shutdown();
        }

        assertThatCode(() -> eventListener.handle(event())).doesNotThrowAnyException();
        assertThat(registry.get("pingdom.recommendation.exposure.dropped")
                .tag("reason", "shutdown").counter().count()).isEqualTo(2d);
        assertThat(eventCount("submission_rejected")).isEqualTo(2d);
        verifyNoInteractions(placeRecommendationExposureService);
    }

    @Test
    void emptyExposureDoesNotCreateSuccessOrDroppedCounts() {
        var empty = new PlaceRecommendationExposureRecordRequestedEvent(
                7L, 35.18d, 128.107d, "empty-request", List.of(), "place-rec-v1");

        eventListener.handle(empty);

        verifyNoInteractions(placeRecommendationExposureService);
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void metricFailureDoesNotPreventPersistenceOrEscapeListener() {
        RecommendationMetrics failingMetrics = mock(RecommendationMetrics.class);
        doThrow(new IllegalStateException("registry unavailable"))
                .when(failingMetrics).recordExposureReceived(2);
        eventListener = new PlaceRecommendationExposureEventListener(
                placeRecommendationExposureService, Runnable::run, failingMetrics);

        assertThatCode(() -> eventListener.handle(event())).doesNotThrowAnyException();

        verify(placeRecommendationExposureService).recordExposures(
                7L, 35.1800d, 128.1070d, "recommendation-request-id",
                List.of(101L, 102L), "place-rec-v1");
    }

    private double eventCount(String stage) {
        return registry.get("pingdom.recommendation.exposure.events").tag("stage", stage).counter().count();
    }

    /** 사용자·좌표·요청 ID·두 장소와 추천 버전이 고정된 노출 요청 이벤트를 생성. */
    private PlaceRecommendationExposureRecordRequestedEvent event() {
        return new PlaceRecommendationExposureRecordRequestedEvent(
                7L,
                35.1800d,
                128.1070d,
                "recommendation-request-id",
                List.of(101L, 102L),
                "place-rec-v1"
        );
    }
}
