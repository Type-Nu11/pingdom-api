package com.typenull.pingdom.moderation.application.service.appeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.AdminPostService;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.application.service.user.sanction.UserSanctionCommandService;
import com.typenull.pingdom.moderation.domain.appeal.ReportAppeal;
import com.typenull.pingdom.moderation.domain.appeal.ReportAppealStatus;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.infrastructure.persistence.ReportAppealRepository;
import com.typenull.pingdom.moderation.infrastructure.persistence.ReportAppealRepository.AppealReference;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportAppealServiceTest {

    private static final long APPEAL_ID = 1L;
    private static final long REPORT_ID = 2L;
    private static final long POST_ID = 3L;
    private static final long TARGET_ID = 4L;
    private static final long ADMIN_ID = 5L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock private ReportAppealRepository appeals;
    @Mock private PostReportRepository reports;
    @Mock private MapImageRepository images;
    @Mock private UserRepository users;
    @Mock private AdminPostService posts;
    @Mock private UserSanctionCommandService sanctions;
    @Mock private AdminAuditLogService audit;
    @Mock private AdminRoleAuthorizationService authorization;
    @Mock private EntityManager entityManager;

    private ReportAppealService service;

    @BeforeEach
    void setUp() {
        service = new ReportAppealService(appeals, reports, images, users, posts, sanctions, audit,
                authorization, entityManager, CLOCK);
    }

    @Test
    void locksReportBeforeAppealAndUserBeforeRestoringPhoto() {
        ReportAppeal appeal = appeal();
        PostReport report = report(PostReportStatus.ACCEPTED);
        User target = User.builder().id(TARGET_ID).build();
        stubApproval(appeal, report, target);

        var response = service.approve(APPEAL_ID, "restore", ADMIN_ID);
        assertAlreadyProcessed(() -> service.reject(APPEAL_ID, "late", ADMIN_ID));

        assertThat(response.status()).isEqualTo(ReportAppealStatus.APPROVED);
        assertThat(report.getStatus()).isEqualTo(PostReportStatus.RESTORED);
        verify(posts, times(1)).restorePost(POST_ID, "restore", ADMIN_ID);
        verifyNoInteractions(sanctions);
        verify(appeals, never()).findById(APPEAL_ID);
        verify(reports, never()).findById(REPORT_ID);
        InOrder order = inOrder(authorization, appeals, reports, entityManager, users, posts);
        order.verify(authorization).requirePermission(ADMIN_ID, AdminPermission.REPORT_REVIEW);
        order.verify(authorization).requirePermission(ADMIN_ID, AdminPermission.USER_SANCTION);
        order.verify(appeals).findReferenceById(APPEAL_ID);
        order.verify(reports).findByIdForUpdate(REPORT_ID);
        order.verify(entityManager).refresh(report);
        order.verify(appeals).findByIdForUpdate(APPEAL_ID);
        order.verify(entityManager).refresh(appeal);
        order.verify(users).findByIdForUpdate(TARGET_ID);
        order.verify(entityManager).refresh(target);
        order.verify(posts).restorePost(POST_ID, "restore", ADMIN_ID);
    }

    @Test
    void rejectionLocksOnlyAppealAndDoesNotRestoreAnything() {
        ReportAppeal appeal = appeal();
        when(appeals.findByIdForUpdate(APPEAL_ID)).thenReturn(Optional.of(appeal));

        var response = service.reject(APPEAL_ID, "reject", ADMIN_ID);

        assertThat(response.status()).isEqualTo(ReportAppealStatus.REJECTED);
        verifyNoInteractions(reports, images, users, posts, sanctions);
        verify(appeals, never()).findReferenceById(anyLong());
        verify(audit).record(eq(ADMIN_ID), eq(AdminAuditAction.APPEAL_REJECTED),
                eq(AdminAuditTargetType.APPEAL), eq(APPEAL_ID), eq("reject"), any(), any());
    }

    @Test
    void latestCompletedStateAfterLockPreventsApprovalSideEffects() {
        ReportAppeal appeal = appeal();
        PostReport report = report(PostReportStatus.ACCEPTED);
        when(appeals.findReferenceById(APPEAL_ID)).thenReturn(Optional.of(reference()));
        when(reports.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(report));
        when(appeals.findByIdForUpdate(APPEAL_ID)).thenReturn(Optional.of(appeal));
        doNothing().when(entityManager).refresh(report);
        doAnswer(invocation -> {
            appeal.reject(9L, "concurrent", NOW);
            return null;
        }).when(entityManager).refresh(appeal);

        assertAlreadyProcessed(() -> service.approve(APPEAL_ID, "restore", ADMIN_ID));

        assertThat(report.getStatus()).isEqualTo(PostReportStatus.ACCEPTED);
        verifyNoInteractions(users, images, posts, sanctions, audit);
    }

    @Test
    void changedConnectionIsRejectedBeforeUserOrPhotoChanges() {
        ReportAppeal appeal = appeal();
        PostReport report = PostReport.builder().id(REPORT_ID).reportedUserId(99L)
                .reportedImageId(POST_ID).status(PostReportStatus.ACCEPTED).build();
        when(appeals.findReferenceById(APPEAL_ID)).thenReturn(Optional.of(reference()));
        when(reports.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(report));
        when(appeals.findByIdForUpdate(APPEAL_ID)).thenReturn(Optional.of(appeal));

        assertThatThrownBy(() -> service.approve(APPEAL_ID, "restore", ADMIN_ID))
                .isInstanceOfSatisfying(AdminException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(AdminErrorCode.APPEAL_NOT_FOUND));

        verifyNoInteractions(users, images, posts, sanctions, audit);
        assertThat(appeal.isSubmitted()).isTrue();
    }

    @Test
    void retainsSanctionWhenAnotherAcceptedReportRemains() {
        ReportAppeal appeal = appeal();
        PostReport report = report(PostReportStatus.ACCEPTED);
        User target = bannedTarget();
        stubApproval(appeal, report, target);
        when(reports.existsByReportedUserIdAndStatusAndIdNot(TARGET_ID, PostReportStatus.ACCEPTED, REPORT_ID))
                .thenReturn(true);

        service.approve(APPEAL_ID, "restore", ADMIN_ID);

        assertThat(target.isCurrentlyBanned(NOW)).isTrue();
        verifyNoInteractions(sanctions);
        verify(reports, never()).existsByReportedUserIdAndStatusAndReason(anyLong(), any(), any());
    }

    @Test
    void releasesMatchingSanctionOnlyAfterAllRelevantLocks() {
        ReportAppeal appeal = appeal();
        PostReport report = report(PostReportStatus.ACCEPTED);
        User target = bannedTarget();
        stubApproval(appeal, report, target);
        when(reports.existsByReportedUserIdAndStatusAndReason(TARGET_ID, PostReportStatus.RESTORED, "report"))
                .thenReturn(true);

        service.approve(APPEAL_ID, "restore", ADMIN_ID);

        verify(sanctions).releaseBan(target, "restore", NOW, ADMIN_ID);
        InOrder order = inOrder(users, posts, sanctions);
        order.verify(users).findByIdForUpdate(TARGET_ID);
        order.verify(posts).restorePost(POST_ID, "restore", ADMIN_ID);
        order.verify(sanctions).releaseBan(target, "restore", NOW, ADMIN_ID);
    }

    @Test
    void mismatchedSanctionReasonIsPreserved() {
        ReportAppeal appeal = appeal();
        User target = bannedTarget();
        stubApproval(appeal, report(PostReportStatus.ACCEPTED), target);

        service.approve(APPEAL_ID, "restore", ADMIN_ID);

        assertThat(target.isCurrentlyBanned(NOW)).isTrue();
        verifyNoInteractions(sanctions);
    }

    @Test
    void keepsExistingAbilityToRestoreAppealForHiddenPhotoWithPendingReport() {
        PostReport pendingReport = report(PostReportStatus.PENDING);
        stubApproval(appeal(), pendingReport, User.builder().id(TARGET_ID).build());

        service.approve(APPEAL_ID, "restore", ADMIN_ID);

        assertThat(pendingReport.getStatus()).isEqualTo(PostReportStatus.RESTORED);
    }

    @Test
    void missingPhotoAbortsBeforeAppealDecisionAndSanctionRelease() {
        ReportAppeal appeal = appeal();
        stubApproval(appeal, report(PostReportStatus.ACCEPTED), bannedTarget());
        doThrow(new AdminException(AdminErrorCode.POST_NOT_FOUND))
                .when(posts).restorePost(POST_ID, "restore", ADMIN_ID);

        assertThatThrownBy(() -> service.approve(APPEAL_ID, "restore", ADMIN_ID))
                .isInstanceOfSatisfying(AdminException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(AdminErrorCode.POST_NOT_FOUND));

        assertThat(appeal.isSubmitted()).isTrue();
        verifyNoInteractions(sanctions, audit);
    }

    private void stubApproval(ReportAppeal appeal, PostReport report, User target) {
        when(appeals.findReferenceById(APPEAL_ID)).thenReturn(Optional.of(reference()));
        when(reports.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(report));
        when(appeals.findByIdForUpdate(APPEAL_ID)).thenReturn(Optional.of(appeal));
        when(users.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.of(target));
    }

    private ReportAppeal appeal() {
        return ReportAppeal.builder().id(APPEAL_ID).reportId(REPORT_ID).postId(POST_ID)
                .targetUserId(TARGET_ID).build();
    }

    private PostReport report(PostReportStatus status) {
        return PostReport.builder().id(REPORT_ID).reportedUserId(TARGET_ID)
                .reportedImageId(POST_ID).status(status).build();
    }

    private User bannedTarget() {
        User user = User.builder().id(TARGET_ID).build();
        user.ban("report", NOW.minusHours(1), null);
        return user;
    }

    private AppealReference reference() {
        return new AppealReference() {
            public Long getReportId() { return REPORT_ID; }
            public Long getPostId() { return POST_ID; }
            public Long getTargetUserId() { return TARGET_ID; }
        };
    }

    private void assertAlreadyProcessed(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AdminException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(AdminErrorCode.APPEAL_ALREADY_PROCESSED));
    }
}
