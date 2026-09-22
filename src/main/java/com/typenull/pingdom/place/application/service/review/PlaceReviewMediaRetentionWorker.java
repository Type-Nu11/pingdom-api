package com.typenull.pingdom.place.application.service.review;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료된 미연결 리뷰 사진 정리를 주기적으로 호출합니다.
 * 오류는 기록하고 다음 스케줄에서 재시도하며 실제 삭제 범위와 배치 제한은 미디어 서비스가 결정합니다.
 */
@Component
@ConditionalOnProperty(prefix = "place.review-media", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PlaceReviewMediaRetentionWorker {

    private final PlaceReviewMediaService mediaService;

    /**
     * 설정된 주기마다 미연결 만료 업로드 정리를 서비스에 위임합니다.
     * 실패는 로그로 남기고 다음 스케줄 호출을 기다리며 이 작업자 자체가 즉시 재시도하거나 트랜잭션을 열지는 않습니다.
     */
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
