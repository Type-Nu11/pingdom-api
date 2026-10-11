package com.typenull.pingdom.community.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 미연결 사진만 만료 정리하며 실패한 실행은 다음 주기에 재시도. */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "community.media", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class CommunityPostMediaRetentionWorker {
    private final CommunityPostMediaService mediaService;

    @Scheduled(fixedDelayString = "${community.media.cleanup-delay:PT1H}",
            initialDelayString = "${community.media.cleanup-initial-delay:PT5M}")
    public void purgeExpiredUploads() {
        try {
            mediaService.purgeExpiredUploads();
        } catch (RuntimeException exception) {
            log.error("커뮤니티 임시 사진 정리에 실패했습니다. 다음 주기에 재시도합니다.", exception);
        }
    }
}
