package com.typenull.pingdom.moderation.application.service.appeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.AdminPostService;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.application.service.user.sanction.UserSanctionCommandService;
import com.typenull.pingdom.moderation.domain.appeal.ReportAppealStatus;
import com.typenull.pingdom.moderation.infrastructure.persistence.ReportAppealRepository;
import com.typenull.pingdom.post.domain.MapImageVisibilityStatus;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ReportAppealServiceSubmitTest {

    private static final long REPORT_ID = 2L;
    private static final long POST_ID = 3L;
    private static final long USER_ID = 4L;

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
                authorization, entityManager, Clock.systemUTC());
    }

    @Test
    void locksAndRefreshesReportBeforeCheckingOrInsertingAppeal() {
        PostReport report = report(PostReportStatus.ACCEPTED);
        when(reports.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(report));
        when(appeals.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.submit(REPORT_ID, "appeal", USER_ID, "target");

        assertThat(result.reportId()).isEqualTo(REPORT_ID);
        assertThat(result.status()).isEqualTo(ReportAppealStatus.SUBMITTED);
        InOrder order = inOrder(reports, entityManager, appeals);
        order.verify(reports).findByIdForUpdate(REPORT_ID);
        order.verify(entityManager).refresh(report);
        order.verify(appeals).existsByReportIdAndAppellantUserIdAndStatus(
                REPORT_ID, USER_ID, ReportAppealStatus.SUBMITTED);
        order.verify(appeals).saveAndFlush(any());
        verify(reports, never()).findById(REPORT_ID);
        verifyNoInteractions(users, posts, sanctions, audit, authorization);
    }

    @Test
    void rechecksLatestReportStateAfterWaitingForLock() {
        PostReport report = report(PostReportStatus.ACCEPTED);
        when(reports.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(report));
        doAnswer(invocation -> {
            report.restore(LocalDateTime.now());
            return null;
        }).when(entityManager).refresh(report);

        assertError(() -> service.submit(REPORT_ID, "appeal", USER_ID, "target"),
                MapErrorCode.REPORT_APPEAL_NOT_ALLOWED);

        verifyNoInteractions(appeals);
    }

    @Test
    void preservesHiddenPhotoEligibilityForUnacceptedReport() {
        when(reports.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(report(PostReportStatus.PENDING)));
        when(images.existsByIdAndVisibilityStatus(POST_ID, MapImageVisibilityStatus.AUTO_HIDDEN)).thenReturn(true);
        when(appeals.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.submit(REPORT_ID, "appeal", USER_ID, "target").status())
                .isEqualTo(ReportAppealStatus.SUBMITTED);
    }

    @Test
    void rejectsAnotherAppellantBeforeDuplicateLookup() {
        when(reports.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(report(PostReportStatus.ACCEPTED)));

        assertError(() -> service.submit(REPORT_ID, "appeal", 99L, "other"),
                MapErrorCode.REPORT_APPEAL_NOT_ALLOWED);

        verifyNoInteractions(appeals, images);
    }

    @Test
    void retainsMissingReportError() {
        assertError(() -> service.submit(REPORT_ID, "appeal", USER_ID, "target"),
                MapErrorCode.REPORT_NOT_FOUND);
        verifyNoInteractions(appeals, images, entityManager);
    }

    @Test
    void rejectsExistingSubmittedAppealWithoutAnotherInsert() {
        when(reports.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(report(PostReportStatus.ACCEPTED)));
        when(appeals.existsByReportIdAndAppellantUserIdAndStatus(
                REPORT_ID, USER_ID, ReportAppealStatus.SUBMITTED)).thenReturn(true);

        assertError(() -> service.submit(REPORT_ID, "appeal", USER_ID, "target"),
                MapErrorCode.REPORT_APPEAL_ALREADY_EXISTS);

        verify(appeals, never()).saveAndFlush(any());
    }

    @Test
    void mapsOnlySubmittedAppealUniqueViolationToExistingConflict() {
        stubInsertFailure(integrityFailure("uq_report_appeal_submitted", "23505"));

        assertError(() -> service.submit(REPORT_ID, "appeal", USER_ID, "target"),
                MapErrorCode.REPORT_APPEAL_ALREADY_EXISTS);
        assertThat(MapErrorCode.REPORT_APPEAL_ALREADY_EXISTS.getStatus().value()).isEqualTo(409);
    }

    @ParameterizedTest
    @CsvSource({"uq_other,23505", "uq_report_appeal_submitted,23503", "uq_report_appeal_submitted,23514"})
    void propagatesUnrelatedConstraintFailures(String constraint, String sqlState) {
        DataIntegrityViolationException failure = integrityFailure(constraint, sqlState);
        stubInsertFailure(failure);

        assertThatThrownBy(() -> service.submit(REPORT_ID, "appeal", USER_ID, "target"))
                .isSameAs(failure);
    }

    @Test
    void doesNotClassifyMessageMentionsAsUniqueMetadata() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("uq_report_appeal_submitted");
        stubInsertFailure(failure);

        assertThatThrownBy(() -> service.submit(REPORT_ID, "appeal", USER_ID, "target"))
                .isSameAs(failure);
    }

    private void stubInsertFailure(DataIntegrityViolationException failure) {
        when(reports.findByIdForUpdate(REPORT_ID)).thenReturn(Optional.of(report(PostReportStatus.ACCEPTED)));
        when(appeals.saveAndFlush(any())).thenThrow(failure);
    }

    private DataIntegrityViolationException integrityFailure(String constraint, String sqlState) {
        return new DataIntegrityViolationException("insert failed", new ConstraintViolationException(
                "constraint failed", new SQLException("constraint failed", sqlState), constraint));
    }

    private PostReport report(PostReportStatus status) {
        return PostReport.builder().id(REPORT_ID).reportedImageId(POST_ID).reportedUserId(USER_ID)
                .status(status).build();
    }

    private void assertError(Runnable action, MapErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(MapException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
