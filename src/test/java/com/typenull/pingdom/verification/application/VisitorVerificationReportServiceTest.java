package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.identity.domain.*;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.verification.api.dto.*;
import com.typenull.pingdom.verification.domain.*;
import com.typenull.pingdom.verification.domain.exception.*;
import com.typenull.pingdom.verification.infrastructure.VisitorVerificationReportRepository;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VisitorVerificationReportServiceTest {
    private final VisitorVerificationReportRepository reportRepository = mock(VisitorVerificationReportRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
    private VisitorVerificationReportService service;

    @BeforeEach
    void setUp() {
        service = new VisitorVerificationReportService(reportRepository, userRepository, placeRepository,
                Clock.fixed(Instant.parse("2026-07-20T06:00:00Z"), ZoneOffset.UTC));
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                User.builder().id(1L).role(UserRole.USER).status(UserStatus.ACTIVE).build()));
        when(userRepository.findById(9L)).thenReturn(Optional.of(
                User.builder().id(9L).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build()));
        when(placeRepository.existsById(2L)).thenReturn(true);
        when(reportRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void activeTouristCanSubmitReportForExistingPlace() {
        var response = service.submit(1L, new VisitorVerificationReportCreateRequest(
                2L, VisitorVerificationReportType.OPERATING_HOURS, " 영업시간이 다릅니다. ", null));

        assertThat(response.status()).isEqualTo(VisitorVerificationReportStatus.SUBMITTED);
        assertThat(response.description()).isEqualTo("영업시간이 다릅니다.");
        verify(reportRepository).saveAndFlush(any(VisitorVerificationReport.class));
    }

    @Test
    void duplicateActiveReportIsRejected() {
        when(reportRepository.existsByReporterUserIdAndPlaceIdAndReportTypeAndStatus(
                1L, 2L, VisitorVerificationReportType.LOCATION, VisitorVerificationReportStatus.SUBMITTED))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submit(1L, new VisitorVerificationReportCreateRequest(
                2L, VisitorVerificationReportType.LOCATION, "위치가 다릅니다.", null)))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.ACTIVE_REPORT_ALREADY_EXISTS);
    }

    @Test
    void nonOwnerCannotReadReport() {
        VisitorVerificationReport report = VisitorVerificationReport.submit(
                3L, 2L, VisitorVerificationReportType.OTHER, "확인이 필요합니다.", null,
                LocalDateTime.of(2026, 7, 20, 15, 0));
        when(reportRepository.findById(5L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.getMine(1L, 5L))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.REPORT_FORBIDDEN);
    }

    @Test
    void adminCanRejectSubmittedReportWithReason() {
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

    @Test
    void touristCannotUseAdminReviewService() {
        assertThatThrownBy(() -> service.review(1L, 5L, new VisitorVerificationReportReviewRequest(
                VisitorVerificationReportStatus.ACCEPTED, null)))
                .isInstanceOf(VisitorVerificationException.class)
                .extracting(exception -> ((VisitorVerificationException) exception).getErrorCode())
                .isEqualTo(VisitorVerificationErrorCode.ADMIN_ACCOUNT_REQUIRED);
        verify(reportRepository, never()).findByIdForUpdate(anyLong());
    }
}
