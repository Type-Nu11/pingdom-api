package com.typenull.pingdom.place.infrastructure.registration;

import com.typenull.pingdom.place.application.service.registration.PlaceRegistrationMediaBackfillService;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * 운영자가 enabled=true로 명시한 경우에만 실행합니다.
 * 처리·건너뜀·실패 건수를 남겨 S3 원본 누락 건을 재처리 대상으로 구분합니다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "place.registration-media-backfill", name = "enabled", havingValue = "true")
class PlaceRegistrationMediaBackfillRunner implements ApplicationRunner {

    private final PlaceRegistrationMediaBackfillProperties properties;
    private final PlaceRegistrationApplicationRepository applicationRepository;
    private final PlaceRegistrationMediaBackfillService backfillService;

    @Override
    public void run(ApplicationArguments args) {
        int processed = 0;
        int skipped = 0;
        int failed = 0;
        int page = 0;
        boolean hasNext;
        do {
            var applications = applicationRepository.findByStatusAndCompletedPlaceIdIsNotNull(
                    PlaceRegistrationStatus.COMPLETED,
                    PageRequest.of(page, properties.batchSize())
            );
            for (var application : applications) {
                try {
                    if (backfillService.backfill(application.getId())) {
                        processed++;
                    } else {
                        skipped++;
                    }
                } catch (RuntimeException exception) {
                    failed++;
                    log.warn("Approved place registration media backfill failed. applicationId={}, placeId={}",
                            application.getId(), application.getCompletedPlaceId(), exception);
                }
            }
            hasNext = applications.hasNext();
            page++;
        } while (hasNext);
        log.info("Approved place registration media backfill completed. processed={}, skipped={}, failed={}",
                processed, skipped, failed);
    }
}
