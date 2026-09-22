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
import com.typenull.pingdom.verification.api.dto.VisitorVerificationReportCorrectionReviewRequest.Decision;
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

    /** 고정 시각과 활성 관광객·관리자 mock을 구성한다. 저장 mock은 전달받은 도메인 객체를 반환한다. */
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

    /**
     * 거절된 제보 작성자는 새 본문으로 SUBMITTED 정정을 제출할 수 있다.
     * 트랜잭션 프록시 없는 단위 테스트에서 제출 메트릭 호출을 확인한다.
     */
    @Test
    void submitReviewedReportCorrection() {
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

    /** 타인 제보의 정정 제출은 REPORT_FORBIDDEN으로 거부한다. */
    @Test
    void rejectNonOwnerSubmission() {
        VisitorVerificationReport report = reportWithStatus(VisitorVerificationReportStatus.ACCEPTED, 3L);
        when(reportRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.submit(
                1L, 5L,
                new VisitorVerificationReportCorrectionRequest("수정", null, null, null, null, null)))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.REPORT_FORBIDDEN);
    }

    /** 미심사 원본의 정정은 CORRECTION_NOT_ALLOWED로 거부하고 정정 저장소와 상호작용하지 않는다. */
    @Test
    void rejectUnreviewedReportCorrection() {
        VisitorVerificationReport report = reportWithStatus(VisitorVerificationReportStatus.SUBMITTED);
        when(reportRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.submit(
                1L, 5L,
                new VisitorVerificationReportCorrectionRequest("수정", null, null, null, null, null)))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.CORRECTION_NOT_ALLOWED);

        verifyNoInteractions(correctionRepository);
    }

    /** 이미 SUBMITTED 정정이 있으면 ACTIVE_CORRECTION_ALREADY_EXISTS로 거부한다. */
    @Test
    void rejectDuplicateActiveCorrection() {
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

    /**
     * 정정 승인 시 정정 ACCEPTED와 원본 SUBMITTED 상태를 반환한다.
     * 원본 상태 메트릭과 감사 기록 호출을 확인하며 실제 DB 커밋은 이 테스트 범위가 아니다.
     */
    @Test
    void resubmitAcceptedCorrection() {
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
                        Decision.ACCEPTED, null));

        assertThat(response.status()).isEqualTo(VisitorVerificationReportCorrectionStatus.ACCEPTED);
        assertThat(response.reportStatus()).isEqualTo(VisitorVerificationReportStatus.SUBMITTED);
        assertThat(report.getStatus()).isEqualTo(VisitorVerificationReportStatus.SUBMITTED);
        verify(metrics).recordReportStatusUpdate(
                VisitorVerificationReportStatus.ACCEPTED, VisitorVerificationReportStatus.SUBMITTED);
        verify(adminAuditLogService).record(any(), any(), any(), any(), any(), any(), any());
    }

    /** 정정 거절 사유를 응답에 담고 원본의 기존 REJECTED 상태는 유지한다. */
    @Test
    void preserveReportOnRejection() {
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
                        Decision.REJECTED, "변경 근거 부족"));

        assertThat(response.status()).isEqualTo(VisitorVerificationReportCorrectionStatus.REJECTED);
        assertThat(response.reportStatus()).isEqualTo(VisitorVerificationReportStatus.REJECTED);
        assertThat(response.reviewNote()).isEqualTo("변경 근거 부족");
        assertThat(report.getStatus()).isEqualTo(VisitorVerificationReportStatus.REJECTED);
    }

    /**
     * 같은 작성자·장소·유형에 다른 미심사 제보가 있으면 정정 승인을 중복 오류로 거부한다.
     * 정정 flush 저장과 감사 기록은 호출하지 않아야 한다.
     */
    @Test
    void rejectConflictingCorrection() {
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
                        Decision.ACCEPTED, null)))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.ACTIVE_REPORT_ALREADY_EXISTS);

        verify(correctionRepository, never()).saveAndFlush(correction);
        verifyNoInteractions(adminAuditLogService);
    }

    /**
     * 정정 승인 flush가 활성 제보 유일 제약에 걸리면 중복 제보 오류를 전달한다.
     * 실패 뒤 감사 기록은 호출하지 않아야 한다.
     */
    @Test
    void mapConcurrentCorrectionConflict() {
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
                        Decision.ACCEPTED, null)))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.ACTIVE_REPORT_ALREADY_EXISTS);

        verifyNoInteractions(adminAuditLogService);
    }

    /** 타인 원본 제보의 정정 이력 조회는 CORRECTION_FORBIDDEN으로 거부한다. */
    @Test
    void rejectNonOwnerHistory() {
        VisitorVerificationReport report = reportWithStatus(VisitorVerificationReportStatus.ACCEPTED, 3L);
        when(reportRepository.findById(5L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.listMine(1L, 5L, 1, 20))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.CORRECTION_FORBIDDEN);
    }

    /**
     * 정정 심사 후 감사 기록이 IllegalStateException으로 실패하면 원래 타입과 메시지를 유지한다.
     * 감사 인프라 오류를 정정 상태 오류로 오인해 변환하는 회귀를 방지한다.
     */
    @Test
    void preserveAuditFailure() {
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
                        Decision.REJECTED, "근거 부족")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");
    }

    /** 작성자 1의 영업시간 제보를 지정 심사 상태로 만들어 반환한다. */
    private VisitorVerificationReport reportWithStatus(VisitorVerificationReportStatus status) {
        return reportWithStatus(status, 1L);
    }

    /**
     * 지정 작성자의 미영속 제보를 만들고 요청 상태가 심사 결과이면 관리자 9로 심사한다.
     * 거절 상태에는 필수 사유를 넣으며 ID는 설정하지 않는다.
     */
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
