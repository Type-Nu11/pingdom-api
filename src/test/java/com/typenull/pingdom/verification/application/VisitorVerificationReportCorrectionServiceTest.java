package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.shared.observability.VisitorVerificationReportMetrics;
import com.typenull.pingdom.verification.api.dto.MyVisitorVerificationReportCorrectionResponse;
import com.typenull.pingdom.verification.api.dto.VisitorVerificationReportCorrectionRequest;
import com.typenull.pingdom.verification.api.dto.VisitorVerificationReportCorrectionReviewRequest;
import com.typenull.pingdom.verification.domain.VisitorVerificationReport;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrection;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrectionStatus;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportStatus;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportType;
import com.typenull.pingdom.verification.domain.exception.VisitorVerificationErrorCode;
import com.typenull.pingdom.verification.domain.exception.VisitorVerificationException;
import com.typenull.pingdom.verification.infrastructure.VisitorVerificationReportCorrectionRepository;
import com.typenull.pingdom.verification.infrastructure.VisitorVerificationReportRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class VisitorVerificationReportCorrectionServiceTest {

    private final VisitorVerificationReportRepository reportRepository = mock(VisitorVerificationReportRepository.class);
    private final VisitorVerificationReportCorrectionRepository correctionRepository =
            mock(VisitorVerificationReportCorrectionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminAuditLogService adminAuditLogService = mock(AdminAuditLogService.class);
    private final VisitorVerificationReportMetrics metrics = mock(VisitorVerificationReportMetrics.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-20T06:00:00Z"), ZoneOffset.UTC);
    private VisitorVerificationReportCorrectionService service;

    @BeforeEach
    void setUp() {
        service = new VisitorVerificationReportCorrectionService(
                reportRepository,
                correctionRepository,
                userRepository,
                clock,
                adminAuditLogService,
                metrics
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                User.builder().id(1L).role(UserRole.USER).status(UserStatus.ACTIVE).build()));
        when(userRepository.findById(9L)).thenReturn(Optional.of(
                User.builder().id(9L).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build()));
        when(correctionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ownerCanSubmitCorrectionForReviewedReport() {
        VisitorVerificationReport report = reportWithStatus(VisitorVerificationReportStatus.REJECTED);
        when(reportRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(report));
        when(correctionRepository.existsByReport_IdAndStatus(
                5L, VisitorVerificationReportCorrectionStatus.SUBMITTED)).thenReturn(false);

        MyVisitorVerificationReportCorrectionResponse response = service.submit(
                1L,
                5L,
                new VisitorVerificationReportCorrectionRequest(
                        "수정된 영업시간", "https://example.com/evidence", null, null, null, null));

        assertThat(response.status()).isEqualTo(VisitorVerificationReportCorrectionStatus.SUBMITTED);
        assertThat(response.description()).isEqualTo("수정된 영업시간");
        verify(metrics).recordCorrectionSubmitted();
    }

    @Test
    void nonOwnerCannotSubmitCorrection() {
        VisitorVerificationReport report = reportWithStatus(VisitorVerificationReportStatus.ACCEPTED, 3L);
        when(reportRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.submit(
                1L, 5L,
                new VisitorVerificationReportCorrectionRequest("수정", null, null, null, null, null)))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.REPORT_FORBIDDEN);
    }

    @Test
    void duplicateActiveCorrectionIsRejected() {
        VisitorVerificationReport report = reportWithStatus(VisitorVerificationReportStatus.ACCEPTED);
        when(reportRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(report));
        when(correctionRepository.existsByReport_IdAndStatus(
                5L, VisitorVerificationReportCorrectionStatus.SUBMITTED)).thenReturn(true);

        assertThatThrownBy(() -> service.submit(
                1L, 5L,
                new VisitorVerificationReportCorrectionRequest("수정", null, null, null, null, null)))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.ACTIVE_CORRECTION_ALREADY_EXISTS);
    }

    @Test
    void acceptingCorrectionResetsOriginalReportToSubmitted() {
        VisitorVerificationReport report = reportWithStatus(VisitorVerificationReportStatus.ACCEPTED);
        VisitorVerificationReportCorrection correction = VisitorVerificationReportCorrection.submit(
                report, 1L, "수정된 대기", null, null, null, null, null,
                LocalDateTime.of(2026, 7, 20, 15, 0));
        when(correctionRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(correction));
        when(reportRepository.findByIdForUpdate(isNull(Long.class))).thenReturn(Optional.of(report));

        var response = service.review(
                9L,
                8L,
                new VisitorVerificationReportCorrectionReviewRequest(
                        VisitorVerificationReportCorrectionStatus.ACCEPTED, null));

        assertThat(response.status()).isEqualTo(VisitorVerificationReportCorrectionStatus.ACCEPTED);
        assertThat(response.reportStatus()).isEqualTo(VisitorVerificationReportStatus.SUBMITTED);
        assertThat(report.getStatus()).isEqualTo(VisitorVerificationReportStatus.SUBMITTED);
        verify(metrics).recordReportStatusUpdate(
                VisitorVerificationReportStatus.ACCEPTED, VisitorVerificationReportStatus.SUBMITTED);
        verify(adminAuditLogService).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectingCorrectionKeepsOriginalReportStatusAndExposesReason() {
        VisitorVerificationReport report = reportWithStatus(VisitorVerificationReportStatus.REJECTED);
        VisitorVerificationReportCorrection correction = VisitorVerificationReportCorrection.submit(
                report, 1L, "다시 수정", null, null, null, null, null,
                LocalDateTime.of(2026, 7, 20, 15, 0));
        when(correctionRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(correction));
        when(reportRepository.findByIdForUpdate(isNull(Long.class))).thenReturn(Optional.of(report));

        var response = service.review(
                9L,
                8L,
                new VisitorVerificationReportCorrectionReviewRequest(
                        VisitorVerificationReportCorrectionStatus.REJECTED, "변경 근거 부족"));

        assertThat(response.status()).isEqualTo(VisitorVerificationReportCorrectionStatus.REJECTED);
        assertThat(response.reportStatus()).isEqualTo(VisitorVerificationReportStatus.REJECTED);
        assertThat(response.reviewNote()).isEqualTo("변경 근거 부족");
        assertThat(report.getStatus()).isEqualTo(VisitorVerificationReportStatus.REJECTED);
    }

    @Test
    void acceptingCorrectionIsRejectedWhenAnotherActiveReportExists() {
        VisitorVerificationReport report = reportWithStatus(VisitorVerificationReportStatus.ACCEPTED);
        VisitorVerificationReportCorrection correction = VisitorVerificationReportCorrection.submit(
                report, 1L, "수정", null, null, null, null, null,
                LocalDateTime.of(2026, 7, 20, 15, 0));
        when(correctionRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(correction));
        when(reportRepository.findByIdForUpdate(isNull(Long.class))).thenReturn(Optional.of(report));
        when(reportRepository.existsByReporterUserIdAndPlaceIdAndReportTypeAndStatusAndIdNot(
                1L, 2L, VisitorVerificationReportType.OPERATING_HOURS,
                VisitorVerificationReportStatus.SUBMITTED, null)).thenReturn(true);

        assertThatThrownBy(() -> service.review(
                9L, 8L,
                new VisitorVerificationReportCorrectionReviewRequest(
                        VisitorVerificationReportCorrectionStatus.ACCEPTED, null)))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.ACTIVE_REPORT_ALREADY_EXISTS);

        verify(correctionRepository, never()).saveAndFlush(correction);
        verifyNoInteractions(adminAuditLogService);
    }

    @Test
    void concurrentActiveReportConstraintIsReportedAsConflict() {
        VisitorVerificationReport report = reportWithStatus(VisitorVerificationReportStatus.ACCEPTED);
        VisitorVerificationReportCorrection correction = VisitorVerificationReportCorrection.submit(
                report, 1L, "수정", null, null, null, null, null,
                LocalDateTime.of(2026, 7, 20, 15, 0));
        ConstraintViolationException constraint = new ConstraintViolationException(
                "duplicate", new SQLException(), "uq_visitor_verification_report_active");
        when(correctionRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(correction));
        when(reportRepository.findByIdForUpdate(isNull(Long.class))).thenReturn(Optional.of(report));
        when(correctionRepository.saveAndFlush(correction)).thenThrow(
                new DataIntegrityViolationException("duplicate", constraint));

        assertThatThrownBy(() -> service.review(
                9L, 8L,
                new VisitorVerificationReportCorrectionReviewRequest(
                        VisitorVerificationReportCorrectionStatus.ACCEPTED, null)))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.ACTIVE_REPORT_ALREADY_EXISTS);

        verifyNoInteractions(adminAuditLogService);
    }

    @Test
    void nonOwnerCannotListCorrections() {
        VisitorVerificationReport report = reportWithStatus(VisitorVerificationReportStatus.ACCEPTED, 3L);
        when(reportRepository.findById(5L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.listMine(1L, 5L, 1, 20))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.CORRECTION_FORBIDDEN);
    }

    @Test
    void auditInfrastructureFailureIsNotMappedToDomainConflict() {
        VisitorVerificationReport report = reportWithStatus(VisitorVerificationReportStatus.REJECTED);
        VisitorVerificationReportCorrection correction = VisitorVerificationReportCorrection.submit(
                report, 1L, "수정", null, null, null, null, null,
                LocalDateTime.of(2026, 7, 20, 15, 0));
        when(correctionRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(correction));
        when(reportRepository.findByIdForUpdate(isNull(Long.class))).thenReturn(Optional.of(report));
        doThrow(new IllegalStateException("audit unavailable"))
                .when(adminAuditLogService).record(any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.review(
                9L, 8L,
                new VisitorVerificationReportCorrectionReviewRequest(
                        VisitorVerificationReportCorrectionStatus.REJECTED, "근거 부족")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");
    }

    private VisitorVerificationReport reportWithStatus(VisitorVerificationReportStatus status) {
        return reportWithStatus(status, 1L);
    }

    private VisitorVerificationReport reportWithStatus(VisitorVerificationReportStatus status, Long reporterUserId) {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                reporterUserId,
                2L,
                VisitorVerificationReportType.OPERATING_HOURS,
                "기존 영업시간",
                null,
                LocalDateTime.of(2026, 7, 20, 14, 0));
        if (status != VisitorVerificationReportStatus.SUBMITTED) {
            report.review(
                    9L,
                    status,
                    status == VisitorVerificationReportStatus.REJECTED ? "기존 사유" : null,
                    LocalDateTime.of(2026, 7, 20, 14, 30));
        }
        return report;
    }
}
