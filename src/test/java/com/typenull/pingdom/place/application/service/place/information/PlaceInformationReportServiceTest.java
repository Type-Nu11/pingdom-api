package com.typenull.pingdom.place.application.service.place.information;

import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationDisputeCreateRequest;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationDisputeResponse;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationDisputeReviewRequest;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationReportCreateRequest;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationReportResponse;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationReportReviewRequest;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationDisputeStatus;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReport;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportDispute;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportReasonType;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportStatus;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportTargetType;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceInformationEvidenceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceInformationReportDisputeRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceInformationReportRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.observability.PlaceInformationMetrics;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceInformationReportServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-21T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);

    @Mock
    private MapPlaceRepository mapPlaceRepository;

    @Mock
    private PlaceInformationEvidenceRepository placeInformationEvidenceRepository;

    @Mock
    private PlaceInformationReportRepository placeInformationReportRepository;

    @Mock
    private PlaceInformationReportDisputeRepository placeInformationReportDisputeRepository;

    @Mock
    private MerchantOwnerPlaceRepository merchantOwnerPlaceRepository;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @Mock
    private AdminAuditLogService adminAuditLogService;

    @Mock
    private PlaceInformationMetrics placeInformationMetrics;

    private PlaceInformationReportService service;

    @BeforeEach
    void setUp() {
        service = new PlaceInformationReportService(
                mapPlaceRepository,
                placeInformationEvidenceRepository,
                placeInformationReportRepository,
                placeInformationReportDisputeRepository,
                merchantOwnerPlaceRepository,
                outboxEventPublisher,
                adminAuditLogService,
                placeInformationMetrics,
                CLOCK
        );
    }

    @Test
    void submitCreatesReportAndPublishesSubmittedEvent() {
        MapPlace place = place(10L, 99L);
        when(mapPlaceRepository.findById(10L)).thenReturn(Optional.of(place));
        when(placeInformationReportRepository.existsByReporterUserIdAndPlace_IdAndTargetTypeAndStatus(
                1L,
                10L,
                PlaceInformationReportTargetType.OPERATING_STATUS,
                PlaceInformationReportStatus.SUBMITTED
        )).thenReturn(false);
        when(placeInformationReportRepository.saveAndFlush(any(PlaceInformationReport.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 200L));

        PlaceInformationReportResponse response = service.submit(
                1L,
                10L,
                new PlaceInformationReportCreateRequest(
                        null,
                        PlaceInformationReportTargetType.OPERATING_STATUS,
                        PlaceInformationReportReasonType.INCORRECT,
                        "영업 상태가 실제와 다릅니다.",
                        "https://example.com/report.jpg"
                )
        );

        assertThat(response.reportId())
                .as("신고 생성 응답에는 저장된 신고 ID가 포함되어야 한다")
                .isEqualTo(200L);
        assertThat(response.status())
                .as("신규 신고는 검토 전 SUBMITTED 상태여야 한다")
                .isEqualTo(PlaceInformationReportStatus.SUBMITTED);
        verify(placeInformationMetrics).recordReportSubmitted(PlaceInformationReportTargetType.OPERATING_STATUS);
        verify(outboxEventPublisher).publish(
                eq("place-information-report:PLACE_INFORMATION_REPORT_SUBMITTED:200:" + NOW),
                eq(OutboxEventType.PLACE_INFORMATION_REPORT_SUBMITTED),
                any(),
                eq("PLACE"),
                eq("10")
        );
    }

    @Test
    void submitRejectsDuplicateActiveReportWithDiagnosticErrorCode() {
        MapPlace place = place(10L, 99L);
        when(mapPlaceRepository.findById(10L)).thenReturn(Optional.of(place));
        when(placeInformationReportRepository.existsByReporterUserIdAndPlace_IdAndTargetTypeAndStatus(
                1L,
                10L,
                PlaceInformationReportTargetType.OPERATING_STATUS,
                PlaceInformationReportStatus.SUBMITTED
        )).thenReturn(true);

        assertThatThrownBy(() -> service.submit(
                1L,
                10L,
                new PlaceInformationReportCreateRequest(
                        null,
                        PlaceInformationReportTargetType.OPERATING_STATUS,
                        PlaceInformationReportReasonType.INCORRECT,
                        "중복 신고입니다.",
                        null
                )
        ))
                .as("중복 active 신고 실패 원인을 errorCode로 식별할 수 있어야 한다")
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.PLACE_INFORMATION_REPORT_ALREADY_SUBMITTED));
        verify(placeInformationReportRepository, never()).saveAndFlush(any());
        verify(outboxEventPublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void submitDisputeRequiresPlaceManagerPermission() {
        PlaceInformationReport report = acceptedReport(200L, place(10L, 99L), 1L);
        when(placeInformationReportRepository.findWithLockById(200L)).thenReturn(Optional.of(report));
        when(merchantOwnerPlaceRepository.existsByPlaceIdAndMerchantOwnerUserId(10L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> service.submitDispute(
                2L,
                200L,
                new PlaceInformationDisputeCreateRequest("점주 반박입니다.", null)
        ))
                .as("장소 관리자나 연결 점주가 아니면 반박 생성 실패 원인을 errorCode로 식별해야 한다")
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.PLACE_INFORMATION_DISPUTE_FORBIDDEN));
        verify(outboxEventPublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void submitDisputePublishesDisputeAndReportDisputedEvents() {
        PlaceInformationReport report = acceptedReport(200L, place(10L, 99L), 1L);
        when(placeInformationReportRepository.findWithLockById(200L)).thenReturn(Optional.of(report));

        PlaceInformationDisputeResponse response = service.submitDispute(
                99L,
                200L,
                new PlaceInformationDisputeCreateRequest("공식 영업 증빙으로 반박합니다.", "https://example.com/dispute.jpg")
        );

        assertThat(response.status())
                .as("반박 생성 응답은 SUBMITTED 상태여야 한다")
                .isEqualTo(PlaceInformationDisputeStatus.SUBMITTED);
        assertThat(report.getStatus())
                .as("반박 제출 시 신고 상태는 DISPUTED로 전환되어야 한다")
                .isEqualTo(PlaceInformationReportStatus.DISPUTED);
        verify(placeInformationMetrics).recordDisputeSubmitted();
        verify(placeInformationMetrics).recordReportStatusUpdate(
                PlaceInformationReportStatus.ACCEPTED,
                PlaceInformationReportStatus.DISPUTED
        );
        verify(outboxEventPublisher).publish(
                eq("place-information-dispute:PLACE_INFORMATION_DISPUTE_SUBMITTED:null:" + NOW),
                eq(OutboxEventType.PLACE_INFORMATION_DISPUTE_SUBMITTED),
                any(),
                eq("PLACE"),
                eq("10")
        );
        verify(outboxEventPublisher).publish(
                eq("place-information-report:PLACE_INFORMATION_REPORT_DISPUTED:200:" + NOW),
                eq(OutboxEventType.PLACE_INFORMATION_REPORT_DISPUTED),
                any(),
                eq("PLACE"),
                eq("10")
        );
    }

    @Test
    void reviewReportWritesAuditMetricAndReviewedEvent() {
        PlaceInformationReport report = submittedReport(200L, place(10L, 99L), 1L);
        when(placeInformationReportRepository.findWithLockById(200L)).thenReturn(Optional.of(report));

        PlaceInformationReportResponse response = service.reviewReport(
                7L,
                200L,
                new PlaceInformationReportReviewRequest(PlaceInformationReportStatus.ACCEPTED, "현장 증빙 확인")
        );

        assertThat(response.status())
                .as("관리자 승인 후 신고 상태는 ACCEPTED여야 한다")
                .isEqualTo(PlaceInformationReportStatus.ACCEPTED);
        verify(adminAuditLogService).record(
                eq(7L),
                eq(AdminAuditAction.PLACE_INFORMATION_REPORT_REVIEWED),
                eq(AdminAuditTargetType.PLACE_INFORMATION_REPORT),
                eq(200L),
                eq("현장 증빙 확인"),
                any(),
                any()
        );
        verify(placeInformationMetrics).recordReportStatusUpdate(
                PlaceInformationReportStatus.SUBMITTED,
                PlaceInformationReportStatus.ACCEPTED
        );
        verify(outboxEventPublisher).publish(
                eq("place-information-report:PLACE_INFORMATION_REPORT_REVIEWED:200:" + NOW),
                eq(OutboxEventType.PLACE_INFORMATION_REPORT_REVIEWED),
                any(),
                eq("PLACE"),
                eq("10")
        );
    }

    @Test
    void reviewDisputeRejectsUnsupportedStatusWithDiagnosticErrorCode() {
        PlaceInformationReport report = acceptedReport(200L, place(10L, 99L), 1L);
        PlaceInformationReportDispute dispute = report.submitDispute(99L, "반박합니다.", null, NOW);
        ReflectionTestUtils.setField(dispute, "id", 300L);
        when(placeInformationReportDisputeRepository.findWithLockByIdAndReport_Id(300L, 200L))
                .thenReturn(Optional.of(dispute));

        assertThatThrownBy(() -> service.reviewDispute(
                7L,
                200L,
                300L,
                new PlaceInformationDisputeReviewRequest(PlaceInformationDisputeStatus.SUBMITTED, "잘못된 검토 상태")
        ))
                .as("반박 검토는 ACCEPTED/REJECTED 외 상태를 명확한 errorCode로 거절해야 한다")
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.PLACE_INFORMATION_DISPUTE_INVALID_REQUEST));
        verify(adminAuditLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
        verify(outboxEventPublisher, never()).publish(any(), any(), any(), any(), any());
    }

    private MapPlace place(Long placeId, Long ownerUserId) {
        return MapPlace.builder()
                .id(placeId)
                .name("테스트 장소")
                .address("서울시 성동구 테스트로 1")
                .latitude(37.5445d)
                .longitude(127.0557d)
                .userId(ownerUserId)
                .registrant("fixture")
                .build();
    }

    private PlaceInformationReport submittedReport(Long reportId, MapPlace place, Long reporterUserId) {
        PlaceInformationReport report = PlaceInformationReport.submit(
                place,
                null,
                reporterUserId,
                PlaceInformationReportTargetType.OPERATING_STATUS,
                PlaceInformationReportReasonType.INCORRECT,
                "영업 상태가 실제와 다릅니다.",
                null,
                NOW.minusDays(1)
        );
        return withId(report, reportId);
    }

    private PlaceInformationReport acceptedReport(Long reportId, MapPlace place, Long reporterUserId) {
        PlaceInformationReport report = submittedReport(reportId, place, reporterUserId);
        report.accept(7L, "관리자 승인", NOW.minusHours(1));
        return report;
    }

    private PlaceInformationReport withId(PlaceInformationReport report, Long reportId) {
        ReflectionTestUtils.setField(report, "id", reportId);
        return report;
    }
}
