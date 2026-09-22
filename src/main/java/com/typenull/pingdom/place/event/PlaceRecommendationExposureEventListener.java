package com.typenull.pingdom.place.event;

import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationExposureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 추천 조회 트랜잭션 커밋 후 실행기에 노출 기록을 제출.
 * 실행기 이름은 outboxExecutor지만 이 이벤트 자체는 메모리 이벤트이므로 영속 Outbox 재시도 대상에서 제외.
 * 제출·저장 실패는 호출자에게 전파 없이 로그로 남기므로 노출 집계 누락 가능.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlaceRecommendationExposureEventListener {

    private final PlaceRecommendationExposureService placeRecommendationExposureService;
    @Qualifier("outboxExecutor")
    private final TaskExecutor outboxExecutor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PlaceRecommendationExposureRecordRequestedEvent event) {
        try {
            outboxExecutor.execute(() -> recordExposuresSafely(event));
        } catch (RuntimeException exception) {
            log.error(
                    "추천 노출 로그 비동기 작업 제출에 실패했습니다. requestId={}, recommendationVersion={}, placeCount={}",
                    event.requestId(),
                    event.recommendationVersion(),
                    event.placeIds().size(),
                    exception
            );
        }
    }

    private void recordExposuresSafely(PlaceRecommendationExposureRecordRequestedEvent event) {
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
            log.error(
                    "추천 노출 로그 비동기 저장에 실패했습니다. requestId={}, recommendationVersion={}, placeCount={}",
                    event.requestId(),
                    event.recommendationVersion(),
                    event.placeIds().size(),
                    exception
            );
        }
    }
}
