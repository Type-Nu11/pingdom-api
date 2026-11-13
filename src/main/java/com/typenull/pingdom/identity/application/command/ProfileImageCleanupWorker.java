package com.typenull.pingdom.identity.application.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "identity.profile-image-storage", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ProfileImageCleanupWorker {
    private final ProfileImageUploadService images;

    @Scheduled(fixedDelayString = "${identity.profile-image-storage.cleanup-delay:PT1H}",
            initialDelayString = "${identity.profile-image-storage.cleanup-initial-delay:PT1H}")
    public void queueCleanup() {
        try { images.queueDueCleanup(); }
        catch (RuntimeException exception) { log.error("프로필 이미지 정리 예약에 실패했습니다.", exception); }
    }
}
