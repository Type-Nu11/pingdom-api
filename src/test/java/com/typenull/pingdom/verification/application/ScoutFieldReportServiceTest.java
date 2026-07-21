package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.observability.ScoutFieldReportMetrics;
import com.typenull.pingdom.verification.api.dto.ScoutFieldReportCreateRequest;
import com.typenull.pingdom.verification.api.dto.ScoutFieldReportReviewRequest;
import com.typenull.pingdom.verification.domain.ScoutFieldReport;
import com.typenull.pingdom.verification.domain.ScoutFieldReportStatus;
import com.typenull.pingdom.verification.domain.ScoutFieldReportType;
import com.typenull.pingdom.verification.domain.exception.VisitorVerificationErrorCode;
import com.typenull.pingdom.verification.domain.exception.VisitorVerificationException;
import com.typenull.pingdom.verification.infrastructure.ScoutFieldReportRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ScoutFieldReportServiceTest {

    private final ScoutFieldReportRepository reportRepository = mock(ScoutFieldReportRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
    private final AdminAuditLogService adminAuditLogService = mock(AdminAuditLogService.class);
    private final ScoutFieldReportMetrics metrics = mock(ScoutFieldReportMetrics.class);
    private final ScoutEligibilityPolicy scoutEligibilityPolicy = mock(ScoutEligibilityPolicy.class);
    private ScoutFieldReportService service;

    @BeforeEach
    void setUp() {
        service = new ScoutFieldReportService(
                reportRepository,
                userRepository,
                placeRepository,
                Clock.fixed(Instant.parse("2026-07-21T06:00:00Z"), ZoneOffset.UTC),
                adminAuditLogService,
                metrics,
                scoutEligibilityPolicy
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                User.builder().id(1L).role(UserRole.USER).status(UserStatus.ACTIVE).build()
        ));
        when(userRepository.findById(9L)).thenReturn(Optional.of(
                User.builder().id(9L).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build()
        ));
        when(placeRepository.existsById(2L)).thenReturn(true);
        when(scoutEligibilityPolicy.isEligible(1L)).thenReturn(true);
        when(reportRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ordinaryUserCannotSubmitScoutFieldReportBeforeScoutQualificationIsAvailable() {
        when(scoutEligibilityPolicy.isEligible(1L)).thenReturn(false);
        assertThatThrownBy(() -> service.submit(1L, new ScoutFieldReportCreateRequest(
                2L,
                ScoutFieldReportType.OPERATING_HOURS,
                " 영업시간이 변경되었습니다. ",
                null
        ))).isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.SCOUT_ACCOUNT_REQUIRED);

        verify(reportRepository, never()).saveAndFlush(any(ScoutFieldReport.class));
    }

    @Test
    void duplicateActiveScoutFieldReportIsRejected() {
        when(reportRepository.existsByScoutUserIdAndPlaceIdAndReportTypeAndStatus(
                1L, 2L, ScoutFieldReportType.LOCATION, ScoutFieldReportStatus.SUBMITTED
        )).thenReturn(true);

        assertThatThrownBy(() -> service.submit(1L, new ScoutFieldReportCreateRequest(
                2L, ScoutFieldReportType.LOCATION, "위치가 다릅니다.", null
        ))).isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.SCOUT_FIELD_REPORT_ALREADY_SUBMITTED);
    }

    @Test
    void concurrentDuplicateConstraintIsReportedAsConflict() {
        ConstraintViolationException constraint = new ConstraintViolationException(
                "duplicate", new SQLException(), "uq_scout_field_report_active"
        );
        when(reportRepository.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("duplicate", constraint)
        );

        assertThatThrownBy(() -> service.submit(1L, new ScoutFieldReportCreateRequest(
                2L, ScoutFieldReportType.SAFETY, "안전 위험 요소", null
        ))).isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.SCOUT_FIELD_REPORT_ALREADY_SUBMITTED);
    }

    @Test
    void nonOwnerCannotReadScoutFieldReport() {
        ScoutFieldReport report = ScoutFieldReport.submit(
                3L,
                2L,
                ScoutFieldReportType.OTHER,
                "확인이 필요합니다.",
                null,
                LocalDateTime.of(2026, 7, 21, 15, 0)
        );
        when(reportRepository.findById(5L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.getMine(1L, 5L))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.SCOUT_FIELD_REPORT_FORBIDDEN);
    }

    @Test
    void adminCanRejectScoutFieldReportWithReason() {
        ScoutFieldReport report = ScoutFieldReport.submit(
                1L,
                2L,
                ScoutFieldReportType.CLOSED_PLACE,
                "폐업한 것 같습니다.",
                null,
                LocalDateTime.of(2026, 7, 21, 15, 0)
        );
        when(reportRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(report));

        var response = service.review(9L, 5L, new ScoutFieldReportReviewRequest(
                ScoutFieldReportStatus.REJECTED,
                "현장 확인 결과 운영 중"
        ));

        assertThat(response.status()).isEqualTo(ScoutFieldReportStatus.REJECTED);
        assertThat(response.reviewerAdminUserId()).isEqualTo(9L);
        assertThat(response.reviewNote()).isEqualTo("현장 확인 결과 운영 중");
        verify(adminAuditLogService).record(
                eq(9L),
                eq(com.typenull.pingdom.moderation.domain.audit.AdminAuditAction.SCOUT_FIELD_REPORT_REVIEWED),
                eq(com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType.SCOUT_FIELD_REPORT),
                isNull(),
                eq("현장 확인 결과 운영 중"),
                any(),
                any()
        );
    }

    @Test
    void nonAdminCannotReviewScoutFieldReport() {
        assertThatThrownBy(() -> service.review(
                1L,
                5L,
                new ScoutFieldReportReviewRequest(ScoutFieldReportStatus.ACCEPTED, null)
        )).isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.ADMIN_ACCOUNT_REQUIRED);

        verify(reportRepository, never()).findByIdForUpdate(any());
    }
}
