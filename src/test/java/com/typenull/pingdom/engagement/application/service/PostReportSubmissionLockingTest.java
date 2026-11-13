package com.typenull.pingdom.engagement.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.event.PostReportCreatedEvent;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.post.application.service.MapImageStateLockService;
import com.typenull.pingdom.post.application.service.MapImageStateLockService.LockedImage;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostReportSubmissionLockingTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock private MapImageStateLockService imageLocks;
    @Mock private PostReportRepository reports;
    @Mock private ApplicationEventPublisher events;
    @Mock private ReportPolicyService policy;

    private PostReportService service;

    @BeforeEach
    void setUp() {
        service = new PostReportService(imageLocks, reports, events, policy, CLOCK);
    }

    @Test
    void locksPolicyAndPhotoBeforeInsertAndRetainsExistingEventOrder() {
        MapImage image = MapImage.builder().id(3L).userId(10L).imageUrl("https://example.test/photo.jpg").build();
        when(imageLocks.findForUpdate(3L)).thenReturn(Optional.of(new LockedImage(image, null)));
        when(reports.saveAndFlush(any(PostReport.class))).thenAnswer(invocation -> {
            PostReport report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", 17L);
            return report;
        });

        service.report(3L, 20L, "reporter", "reason");

        var order = inOrder(policy, imageLocks, reports, events);
        order.verify(policy).lockForSubmission(20L, "reporter", NOW);
        order.verify(imageLocks).findForUpdate(3L);
        order.verify(reports).existsByReporterUserIdAndMapImage_Id(20L, 3L);
        order.verify(reports).saveAndFlush(any(PostReport.class));
        order.verify(events).publishEvent(new PostReportCreatedEvent(17L));
        order.verify(policy).recordSubmitted(20L, "reporter");
        order.verify(policy).autoHideIfNeeded(image, NOW);
    }

    @Test
    void restrictionRejectsBeforeAcquiringPhotoLock() {
        doThrow(new MapException(MapErrorCode.REPORTER_RESTRICTED))
                .when(policy).lockForSubmission(20L, "reporter", NOW);

        assertThatThrownBy(() -> service.report(3L, 20L, "reporter", "reason"))
                .isInstanceOf(MapException.class);

        verifyNoInteractions(imageLocks, reports, events);
    }

    @Test
    void photoHiddenWhileWaitingCannotReceiveNewReport() {
        MapImage image = MapImage.builder().id(3L).build();
        image.autoHide("earlier decision", NOW.minusSeconds(1), null);
        when(imageLocks.findForUpdate(3L)).thenReturn(Optional.of(new LockedImage(image, null)));

        assertThatThrownBy(() -> service.report(3L, 20L, "reporter", "reason"))
                .isInstanceOfSatisfying(MapException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getErrorCode())
                                .isEqualTo(MapErrorCode.IMAGE_NOT_FOUND));

        verifyNoInteractions(reports, events);
        verify(policy, never()).recordSubmitted(20L, "reporter");
    }
}
