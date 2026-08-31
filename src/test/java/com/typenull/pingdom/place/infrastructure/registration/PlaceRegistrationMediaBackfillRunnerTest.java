package com.typenull.pingdom.place.infrastructure.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.application.service.registration.PlaceRegistrationMediaBackfillService;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PlaceRegistrationMediaBackfillRunnerTest {

    private final PlaceRegistrationApplicationRepository applicationRepository = org.mockito.Mockito.mock(
            PlaceRegistrationApplicationRepository.class);
    private final PlaceRegistrationMediaBackfillService backfillService = org.mockito.Mockito.mock(
            PlaceRegistrationMediaBackfillService.class);
    private final PlaceRegistrationMediaBackfillRunner runner = new PlaceRegistrationMediaBackfillRunner(
            new PlaceRegistrationMediaBackfillProperties(true, 100),
            applicationRepository,
            backfillService
    );

    @Test
    void summarizesProcessedSkippedAndFailedApplicationsSeparately() {
        PlaceRegistrationApplication processed = application(77L, 70069L);
        PlaceRegistrationApplication skipped = application(78L, 70070L);
        PlaceRegistrationApplication failed = application(79L, 70071L);
        when(applicationRepository.findByStatusAndCompletedPlaceIdIsNotNull(
                org.mockito.ArgumentMatchers.eq(PlaceRegistrationStatus.COMPLETED),
                org.mockito.ArgumentMatchers.any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of(processed, skipped, failed)));
        when(backfillService.backfill(77L)).thenReturn(
                new PlaceRegistrationMediaBackfillService.BackfillResult(true, 2, 0)
        );
        when(backfillService.backfill(78L)).thenReturn(
                new PlaceRegistrationMediaBackfillService.BackfillResult(false, 0, 2)
        );
        when(backfillService.backfill(79L)).thenThrow(new IllegalStateException("S3 source object missing"));

        var summary = runner.runBackfill();

        assertThat(summary.processedApplications()).isOne();
        assertThat(summary.skippedApplications()).isOne();
        assertThat(summary.failedApplications()).isOne();
        assertThat(summary.promotedMedia()).isEqualTo(2);
        assertThat(summary.alreadyPromotedMedia()).isEqualTo(2);
    }

    private PlaceRegistrationApplication application(Long applicationId, Long placeId) {
        PlaceRegistrationApplication application = org.mockito.Mockito.mock(PlaceRegistrationApplication.class);
        when(application.getId()).thenReturn(applicationId);
        when(application.getCompletedPlaceId()).thenReturn(placeId);
        return application;
    }
}
