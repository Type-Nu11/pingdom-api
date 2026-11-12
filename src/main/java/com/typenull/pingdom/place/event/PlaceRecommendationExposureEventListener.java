package com.typenull.pingdom.place.event;

import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationExposureService;
import com.typenull.pingdom.shared.observability.RecommendationMetrics;
import com.typenull.pingdom.shared.observability.RecommendationMetrics.ExposureRejectionReason;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 추천 조회 트랜잭션 커밋 후 실행기에 노출 기록을 제출.
 * 영속 Outbox와 분리된 유한 실행기를 사용하며 메모리 이벤트는 재시도 대상에서 제외.
 * 제출·저장 실패는 호출자에게 전파 없이 로그로 남기므로 노출 집계 누락 가능.
 */
@Component
@Slf4j
public class PlaceRecommendationExposureEventListener {

    private final PlaceRecommendationExposureService placeRecommendationExposureService;
    private final TaskExecutor exposureExecutor;
    private final RecommendationMetrics metrics;

    public PlaceRecommendationExposureEventListener(
            PlaceRecommendationExposureService placeRecommendationExposureService,
            @Qualifier("placeRecommendationExposureExecutor") TaskExecutor exposureExecutor,
            RecommendationMetrics metrics
    ) {
        this.placeRecommendationExposureService = placeRecommendationExposureService;
        this.exposureExecutor = exposureExecutor;
        this.metrics = metrics;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void handle(PlaceRecommendationExposureRecordRequestedEvent event) {
        if (event.placeIds().isEmpty()) {
            return;
        }
        int placeCount = event.placeIds().size();
        observe(() -> metrics.recordExposureReceived(placeCount));
        long submittedAt = System.nanoTime();
        try {
            exposureExecutor.execute(() -> recordExposuresSafely(event, submittedAt));
            observe(() -> metrics.recordExposureSubmitted(placeCount));
        } catch (RejectedExecutionException exception) {
            observe(() -> metrics.recordExposureRejected(placeCount, rejectionReason()));
            log.error(
                    "추천 노출 실행기가 작업을 거절했습니다. requestId={}, recommendationVersion={}, placeCount={}",
                    event.requestId(), event.recommendationVersion(), placeCount, exception
            );
        } catch (RuntimeException exception) {
            observe(() -> metrics.recordExposureSubmissionFailure(placeCount));
            log.error(
                    "추천 노출 로그 비동기 작업 제출에 실패했습니다. requestId={}, recommendationVersion={}, placeCount={}",
                    event.requestId(),
                    event.recommendationVersion(),
                    event.placeIds().size(),
                    exception
            );
        }
    }

    private void recordExposuresSafely(
            PlaceRecommendationExposureRecordRequestedEvent event,
            long submittedAt
    ) {
        long startedAt = System.nanoTime();
        int placeCount = event.placeIds().size();
        observe(() -> metrics.recordExposureStarted(placeCount, startedAt - submittedAt));
        try {
            placeRecommendationExposureService.recordExposures(
                    event.userId(),
                    event.latitude(),
                    event.longitude(),
                    event.requestId(),
                    event.placeIds(),
                    event.recommendationVersion()
            );
        } catch (Exception exception) {
            observe(() -> metrics.recordExposurePersistenceFailure(placeCount, System.nanoTime() - startedAt));
            log.error(
                    "추천 노출 로그 비동기 저장에 실패했습니다. requestId={}, recommendationVersion={}, placeCount={}",
                    event.requestId(),
                    event.recommendationVersion(),
                    event.placeIds().size(),
                    exception
            );
            return;
        }
        // REQUIRES_NEW 서비스 프록시가 커밋까지 반환한 뒤에만 저장 성공을 집계합니다.
        observe(() -> metrics.recordExposurePersisted(placeCount, System.nanoTime() - startedAt));
    }

    private void observe(Runnable observation) {
        try {
            observation.run();
        } catch (RuntimeException exception) {
            log.warn("추천 노출 관측 지표 기록에 실패했습니다.", exception);
        }
    }

    private ExposureRejectionReason rejectionReason() {
        if (exposureExecutor instanceof ThreadPoolTaskExecutor executor) {
            try {
                // 거절을 관측한 시점의 풀 상태이며 직전 포화와 종료의 경합 원인을 재구성하지 않습니다.
                return executor.getThreadPoolExecutor().isShutdown()
                        ? ExposureRejectionReason.SHUTDOWN : ExposureRejectionReason.CAPACITY;
            } catch (IllegalStateException exception) {
                return ExposureRejectionReason.UNKNOWN;
            }
        }
        return ExposureRejectionReason.UNKNOWN;
    }
}
