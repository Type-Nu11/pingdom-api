package com.typenull.pingdom.moderation.application.service.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.application.service.ReportPolicyService;
import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.AdminPostService;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.application.service.user.sanction.UserSanctionCommandService;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.outbox.notification.AdminNotificationOutboxPublisher;
import com.typenull.pingdom.post.application.service.MapImageStateLockService;
import com.typenull.pingdom.post.application.service.MapImageStateLockService.LockedImage;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminReportLockingTest {

    private static final long ADMIN_ID = 99L;
    private static final long IMAGE_ID = 9L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);

    @Mock private PostReportRepository reports;
    @Mock private MapImageRepository images;
    @Mock private MapImageStateLockService imageLocks;
    @Mock private UserRepository users;
    @Mock private AdminPostService posts;
    @Mock private UserSanctionCommandService sanctions;
    @Mock private AdminAuditLogService audit;
    @Mock private ReportPolicyService policy;
    @Mock private AdminNotificationOutboxPublisher notifications;
    @Mock private AdminRoleAuthorizationService authorization;
    @Mock private EntityManager entityManager;

    private AdminReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminReportServiceImpl(reports, images, imageLocks, users, posts, sanctions,
                audit, policy, notifications, authorization, entityManager, CLOCK);
    }

    @Test
    void acceptsOnlyOnceAndRejectsLaterDeclineBeforeSideEffects() {
        PostReport report = report(1L, 30L);
        User owner = user(10L);
        User reporter = user(30L);
        MapImage image = MapImage.builder().id(IMAGE_ID).build();
        when(reports.findByIdForUpdate(1L)).thenReturn(Optional.of(report));
        when(users.findByIdForUpdate(10L)).thenReturn(Optional.of(owner));
        when(users.findByIdForUpdate(30L)).thenReturn(Optional.of(reporter));
        when(imageLocks.findForUpdate(IMAGE_ID)).thenReturn(Optional.of(new LockedImage(image, null)));

        service.acceptReport(1L, ADMIN_ID);
        assertThatThrownBy(() -> service.declineReport(1L, ADMIN_ID)).isInstanceOfSatisfying(AdminException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(AdminErrorCode.REPORT_ALREADY_PROCESSED));

        assertThat(report.getStatus()).isEqualTo(PostReportStatus.ACCEPTED);
        assertThat(reporter.getReportCount()).isEqualTo(1L);
        verify(policy).recordAccepted(30L, "reporter-30");
        verify(policy, never()).recordDeclined(anyLong(), any(), any());
        verify(notifications).publishReportProcessed(1L, IMAGE_ID, PostReportStatus.ACCEPTED);
        verify(reports, never()).findById(1L);
        InOrder order = inOrder(reports, policy, users, imageLocks);
        order.verify(reports).findByIdForUpdate(1L);
        order.verify(policy).lockForModeration(List.of(report));
        order.verify(users).findByIdForUpdate(10L);
        order.verify(users).findByIdForUpdate(30L);
        order.verify(imageLocks).findForUpdate(IMAGE_ID);
        order.verify(policy).recordAccepted(30L, "reporter-30");
    }

    @Test
    void bulkLocksAllUsersInIdOrderBeforeAnyReportChanges() {
        PostReport first = report(1L, 50L);
        PostReport second = report(2L, 20L);
        List<PostReport> pending = List.of(first, second);
        User owner = user(10L);
        User earlierReporter = user(20L);
        User laterReporter = user(50L);
        MapImage image = MapImage.builder().id(IMAGE_ID).build();
        when(images.existsById(IMAGE_ID)).thenReturn(true);
        when(reports.findAllByImageAndStatusForUpdate(IMAGE_ID, PostReportStatus.PENDING)).thenReturn(pending);
        when(users.findByIdForUpdate(10L)).thenReturn(Optional.of(owner));
        when(users.findByIdForUpdate(20L)).thenReturn(Optional.of(earlierReporter));
        when(users.findByIdForUpdate(50L)).thenReturn(Optional.of(laterReporter));
        when(imageLocks.findForUpdate(IMAGE_ID)).thenAnswer(invocation -> {
            assertThat(pending).allMatch(PostReport::isPending);
            return Optional.of(new LockedImage(image, null));
        });
        doAnswer(invocation -> {
            image.autoHide("REPORT_BULK_ACCEPTED", LocalDateTime.now(CLOCK), ADMIN_ID);
            return null;
        }).when(posts).hidePost(IMAGE_ID, "REPORT_BULK_ACCEPTED", ADMIN_ID);

        var result = service.acceptPostReports(IMAGE_ID, ADMIN_ID);

        assertThat(result.processedReportCount()).isEqualTo(2);
        assertThat(pending).allMatch(report -> report.getStatus() == PostReportStatus.ACCEPTED);
        assertThat(earlierReporter.getReportCount()).isEqualTo(1L);
        assertThat(laterReporter.getReportCount()).isEqualTo(1L);
        InOrder order = inOrder(reports, policy, users, entityManager, imageLocks);
        order.verify(reports).findAllByImageAndStatusForUpdate(IMAGE_ID, PostReportStatus.PENDING);
        order.verify(policy).lockForModeration(pending);
        order.verify(users).findByIdForUpdate(10L);
        order.verify(entityManager).refresh(owner);
        order.verify(users).findByIdForUpdate(20L);
        order.verify(entityManager).refresh(earlierReporter);
        order.verify(users).findByIdForUpdate(50L);
        order.verify(entityManager).refresh(laterReporter);
        order.verify(imageLocks).findForUpdate(IMAGE_ID);
        order.verify(policy).recordAccepted(50L, "reporter-50");
        order.verify(policy).recordAccepted(20L, "reporter-20");
        verify(sanctions).applyBan(owner, "REPORT_BULK_ACCEPTED", LocalDateTime.now(CLOCK), null, ADMIN_ID);
        verify(notifications, times(2)).publishReportProcessed(anyLong(), eq(IMAGE_ID), eq(PostReportStatus.ACCEPTED));
    }

    @Test
    void singleDeclineRetainsSupportForDeletedPhotoAndMissingReportedUser() {
        PostReport report = report(1L, 30L);
        when(reports.findByIdForUpdate(1L)).thenReturn(Optional.of(report));
        when(users.findByIdForUpdate(10L)).thenReturn(Optional.empty());
        when(users.findByIdForUpdate(30L)).thenReturn(Optional.of(user(30L)));

        var result = service.declineReport(1L, ADMIN_ID);

        assertThat(result.status()).isEqualTo(PostReportStatus.DECLINED);
        assertThat(result.banned()).isFalse();
        verifyNoInteractions(images, imageLocks, sanctions, posts);
        verify(policy).recordDeclined(30L, "reporter-30", LocalDateTime.now(CLOCK));
    }

    @Test
    void emptyBulkDoesNotStartParticipantOrPhotoMutations() {
        when(images.existsById(IMAGE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.declinePostReports(IMAGE_ID, ADMIN_ID))
                .isInstanceOfSatisfying(AdminException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(AdminErrorCode.PENDING_REPORT_NOT_FOUND));

        verifyNoInteractions(policy, users, imageLocks, sanctions, posts, notifications, audit);
    }

    @Test
    void missingReporterFailsBeforeChangingReportOrPhoto() {
        PostReport report = report(1L, 30L);
        when(reports.findByIdForUpdate(1L)).thenReturn(Optional.of(report));
        when(users.findByIdForUpdate(10L)).thenReturn(Optional.of(user(10L)));

        assertThatThrownBy(() -> service.acceptReport(1L, ADMIN_ID)).isInstanceOf(AuthException.class);

        assertThat(report.isPending()).isTrue();
        verifyNoInteractions(imageLocks, posts, sanctions, notifications, audit);
        verify(policy, never()).recordAccepted(anyLong(), any());
    }

    private PostReport report(long id, long reporterId) {
        return PostReport.builder().id(id).reportedImageId(IMAGE_ID).reportedUserId(10L)
                .reporterUserId(reporterId).reporterUsername("reporter-" + reporterId).reason("reason").build();
    }

    private User user(long id) {
        return User.builder().id(id).username("user-" + id).build();
    }
}
