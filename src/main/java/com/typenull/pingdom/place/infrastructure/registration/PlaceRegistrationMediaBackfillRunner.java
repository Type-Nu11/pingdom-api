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
        BackfillSummary summary = runBackfill();
        log.info(
                "Approved place registration media backfill completed. processedApplications={}, "
                        + "skippedApplications={}, failedApplications={}, promotedMedia={}, alreadyPromotedMedia={}",
                summary.processedApplications(),
                summary.skippedApplications(),
                summary.failedApplications(),
                summary.promotedMedia(),
                summary.alreadyPromotedMedia()
        );
    }

    BackfillSummary runBackfill() {
        int processedApplications = 0;
        int skippedApplications = 0;
        int failedApplications = 0;
        int promotedMedia = 0;
        int alreadyPromotedMedia = 0;
        int page = 0;
        boolean hasNext;
        do {
            var applications = applicationRepository.findByStatusAndCompletedPlaceIdIsNotNull(
                    PlaceRegistrationStatus.COMPLETED,
                    PageRequest.of(page, properties.batchSize())
            );
            for (var application : applications) {
                try {
                    var result = backfillService.backfill(application.getId());
                    promotedMedia += result.promotedMediaCount();
                    alreadyPromotedMedia += result.alreadyPromotedMediaCount();
                    if (result.processed()) {
                        processedApplications++;
                    } else {
                        skippedApplications++;
                    }
                } catch (RuntimeException exception) {
                    failedApplications++;
                    log.warn("Approved place registration media backfill failed. applicationId={}, placeId={}",
                            application.getId(), application.getCompletedPlaceId(), exception);
                }
            }
            hasNext = applications.hasNext();
            page++;
        } while (hasNext);
        return new BackfillSummary(
                processedApplications,
                skippedApplications,
                failedApplications,
                promotedMedia,
                alreadyPromotedMedia
        );
    }

    record BackfillSummary(
            int processedApplications,
            int skippedApplications,
            int failedApplications,
            int promotedMedia,
            int alreadyPromotedMedia
    ) {
    }
}
