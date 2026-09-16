package com.typenull.pingdom.place.application.service.review;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "place.review-media", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PlaceReviewMediaRetentionWorker {

    private final PlaceReviewMediaService mediaService;

    @Scheduled(
            fixedDelayString = "${place.review-media.cleanup-delay:PT1H}",
            initialDelayString = "${place.review-media.cleanup-initial-delay:PT5M}"
    )
    public void purgeExpiredUploads() {
        try {
            mediaService.purgeExpiredUploads();
        } catch (Exception exception) {
            log.error("만료된 장소 리뷰 사진 정리 배치가 실패했습니다. 다음 스케줄에서 재시도합니다.", exception);
        }
    }
}
