package com.typenull.pingdom.place.event;

import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationExposureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlaceRecommendationExposureEventListener {

    private final PlaceRecommendationExposureService placeRecommendationExposureService;
    @Qualifier("outboxExecutor")
    private final TaskExecutor outboxExecutor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PlaceRecommendationExposureRecordRequestedEvent event) {
        outboxExecutor.execute(() -> recordExposuresSafely(event));
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
