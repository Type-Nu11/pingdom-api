package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.identity.domain.*;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.shared.observability.VisitorVerificationReportMetrics;
import com.typenull.pingdom.verification.api.dto.*;
import com.typenull.pingdom.verification.domain.*;
import com.typenull.pingdom.verification.domain.exception.*;
import com.typenull.pingdom.verification.infrastructure.VisitorVerificationReportRepository;
import java.time.*;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VisitorVerificationReportServiceTest {
    private final VisitorVerificationReportRepository reportRepository = mock(VisitorVerificationReportRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
    private final AdminAuditLogService adminAuditLogService = mock(AdminAuditLogService.class);
    private final VisitorVerificationReportMetrics metrics = mock(VisitorVerificationReportMetrics.class);
    private VisitorVerificationReportService service;

    /** 고정 시각과 활성 관광객·관리자 mock을 구성. 저장 mock은 전달받은 도메인 객체를 반환. */
    @BeforeEach
    void setUp() {
        service = new VisitorVerificationReportService(
                reportRepository,
                userRepository,
                placeRepository,
                Clock.fixed(Instant.parse("2026-07-20T06:00:00Z"), ZoneOffset.UTC),
                adminAuditLogService,
                metrics
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                User.builder().id(1L).role(UserRole.USER).status(UserStatus.ACTIVE).build()));
        when(userRepository.findById(9L)).thenReturn(Optional.of(
                User.builder().id(9L).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build()));
        when(placeRepository.existsById(2L)).thenReturn(true);
        when(reportRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** 활성 관광객이 존재하는 장소를 제보하면 SUBMITTED와 공백 정리된 본문을 반환하고 flush 저장. */
    @Test
    void submitTouristReport() {
        var response = service.submit(1L, new VisitorVerificationReportCreateRequest(
                2L, VisitorVerificationReportType.OPERATING_HOURS, " 영업시간이 다릅니다. ", null,
                null, null, null, null));

        assertThat(response.status()).isEqualTo(VisitorVerificationReportStatus.SUBMITTED);
        assertThat(response.description()).isEqualTo("영업시간이 다릅니다.");
        verify(reportRepository).saveAndFlush(any(VisitorVerificationReport.class));
    }

    /** 같은 작성자·장소·유형의 미심사 제보가 있으면 ACTIVE_REPORT_ALREADY_EXISTS로 거부. */
    @Test
    void rejectDuplicateActiveReport() {
        when(reportRepository.existsByReporterUserIdAndPlaceIdAndReportTypeAndStatus(
                1L, 2L, VisitorVerificationReportType.LOCATION, VisitorVerificationReportStatus.SUBMITTED))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submit(1L, new VisitorVerificationReportCreateRequest(
                2L, VisitorVerificationReportType.LOCATION, "위치가 다릅니다.", null,
                null, null, null, null)))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.ACTIVE_REPORT_ALREADY_EXISTS);
    }

    /** 혼잡도 FULL을 제출하면 응답에도 같은 enum을 담고 대기 시간은 null이어야 함. */
    @Test
    void returnCrowdLevel() {
        var response = service.submit(1L, new VisitorVerificationReportCreateRequest(
                2L, VisitorVerificationReportType.CROWD_LEVEL, "현재 매우 혼잡합니다.", null,
                null, null, null, CrowdLevel.FULL));

        assertThat(response.crowdLevel()).isEqualTo(CrowdLevel.FULL);
        assertThat(response.waitTimeMinutes()).isNull();
    }

    /** 쿠폰 사용 가능 제보의 AVAILABLE 상태가 응답에 유지되는지 확인. */
    @Test
    void returnCouponUsage() {
        var response = service.submit(1L, new VisitorVerificationReportCreateRequest(
                2L, VisitorVerificationReportType.COUPON_USAGE, "쿠폰 사용 가능", null,
                null, null, CouponUsageStatus.AVAILABLE, null));

        assertThat(response.couponUsageStatus()).isEqualTo(CouponUsageStatus.AVAILABLE);
    }

    /** 대기 시간 유형에 쿠폰 상태를 전달하면 INVALID_REPORT_DETAILS로 변환. */
    @Test
    void rejectMismatchedStructuredValue() {
        assertThatThrownBy(() -> service.submit(1L, new VisitorVerificationReportCreateRequest(
                2L, VisitorVerificationReportType.WAIT_TIME, "대기 시간 제보", null,
                null, null, CouponUsageStatus.AVAILABLE, null)))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.INVALID_REPORT_DETAILS);
    }

    /** flush에서 활성 제보 유일 제약 위반이 발생하면 사전 중복과 같은 오류로 전달. */
    @Test
    void mapConcurrentReportDuplicate() {
        ConstraintViolationException constraint = new ConstraintViolationException(
                "duplicate", new SQLException(), "uq_visitor_verification_report_active");
        when(reportRepository.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("duplicate", constraint));

        assertThatThrownBy(() -> service.submit(1L, new VisitorVerificationReportCreateRequest(
                2L, VisitorVerificationReportType.CROWD_LEVEL, "혼잡도 제보", null,
                null, null, null, CrowdLevel.HIGH)))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.ACTIVE_REPORT_ALREADY_EXISTS);
    }

    /** 다른 작성자의 제보를 본인 조회로 요청하면 REPORT_FORBIDDEN으로 거부. */
    @Test
    void rejectNonOwnerRead() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                3L, 2L, VisitorVerificationReportType.OTHER, "확인이 필요합니다.", null,
                LocalDateTime.of(2026, 7, 20, 15, 0));
        when(reportRepository.findById(5L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.getMine(1L, 5L))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.REPORT_FORBIDDEN);
    }

    /** 관리자 거절 결과에는 REJECTED 상태·심사자 9·거절 사유가 반환되어야 함. */
    @Test
    void rejectReportWithReason() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                1L, 2L, VisitorVerificationReportType.CLOSED_PLACE, "폐업했습니다.", null,
                LocalDateTime.of(2026, 7, 20, 15, 0));
        when(reportRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(report));

        var response = service.review(9L, 5L, new VisitorVerificationReportReviewRequest(
                VisitorVerificationReportStatus.REJECTED, "운영 중 확인"));

        assertThat(response.status()).isEqualTo(VisitorVerificationReportStatus.REJECTED);
        assertThat(response.reviewerAdminUserId()).isEqualTo(9L);
        assertThat(response.reviewNote()).isEqualTo("운영 중 확인");
    }

    /** 관광객의 관리자 심사 요청에 대한 계정 권한 오류와 잠금 조회 미호출 확인. */
    @Test
    void rejectTouristReview() {
        assertThatThrownBy(() -> service.review(1L, 5L, new VisitorVerificationReportReviewRequest(
                VisitorVerificationReportStatus.ACCEPTED, null)))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.ADMIN_ACCOUNT_REQUIRED);
        verify(reportRepository, never()).findByIdForUpdate(anyLong());
    }
}
