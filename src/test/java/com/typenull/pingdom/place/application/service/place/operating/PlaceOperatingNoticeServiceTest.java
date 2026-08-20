package com.typenull.pingdom.place.application.service.place.operating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceCapability;
import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceCapabilityPolicy;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeCreateRequest;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeResponse;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNotice;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeSeverity;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeStatus;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeType;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceOperatingNoticeRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.observability.PlaceOperatingNoticeMetrics;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceOperatingNoticeServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 21, 12, 0);

    @Mock private MapPlaceRepository placeRepository;
    @Mock private PlaceOperatingNoticeRepository noticeRepository;
    @Mock private MerchantOwnerPlaceRepository merchantOwnerPlaceRepository;
    @Mock private MerchantPlaceCapabilityPolicy merchantPlaceCapabilityPolicy;
    @Mock private OutboxEventPublisher outboxEventPublisher;
    @Mock private AdminAuditLogService adminAuditLogService;
    @Mock private PlaceOperatingNoticeMetrics metrics;
    @Mock private PlaceOperatingHoursEvaluator hoursEvaluator;
    @Mock private Clock clock;

    @InjectMocks
    private PlaceOperatingNoticeService service;

    @Test
    void rejectsMerchantWhoDoesNotOwnThePlace() {
        MapPlace place = place();
        when(placeRepository.findById(10L)).thenReturn(Optional.of(place));
        when(merchantOwnerPlaceRepository.existsByPlaceIdAndMerchantOwnerUserId(10L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> service.createByMerchant(99L, 10L, request()))
                .as("다른 점주는 상점 운영 공지를 생성할 수 없어야 한다")
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.PLACE_OPERATING_NOTICE_FORBIDDEN));
        verify(noticeRepository, never()).saveAndFlush(any());
    }

    @Test
    void adminCanCreateNoticeAndReturnsItsLifecycleStatus() {
        fixedClock();
        MapPlace place = place();
        when(placeRepository.findById(10L)).thenReturn(Optional.of(place));
        when(noticeRepository.saveAndFlush(any(PlaceOperatingNotice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createByAdmin(7L, 10L, request());

        assertThat(response.status())
                .as("현재 시각에 시작한 관리자 공지는 즉시 ACTIVE여야 한다")
                .isEqualTo(PlaceOperatingNoticeStatus.ACTIVE);
        verify(outboxEventPublisher).publish(any(), any(), any(), any(), any());
        verify(adminAuditLogService).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void lifecycleCommandActivatesScheduledAndExpiresDueNotices() {
        LocalDateTime lifecycleNow = NOW.plusHours(2);
        when(clock.instant()).thenReturn(lifecycleNow.toInstant(ZoneOffset.UTC));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        MapPlace place = place();
        PlaceOperatingNotice scheduled = PlaceOperatingNotice.create(
                place, PlaceOperatingNoticeType.GENERAL, PlaceOperatingNoticeSeverity.INFO,
                "오늘 운영 안내", NOW.plusHours(1), NOW.plusHours(3), 1L, NOW);
        PlaceOperatingNotice expiring = PlaceOperatingNotice.create(
                place, PlaceOperatingNoticeType.TEMPORARY_CLOSURE, PlaceOperatingNoticeSeverity.WARNING,
                "곧 영업을 종료합니다", NOW.minusHours(1), lifecycleNow, 1L, NOW.minusHours(2));
        when(noticeRepository.findActivatableNoticesForUpdate(PlaceOperatingNoticeStatus.SCHEDULED, lifecycleNow))
                .thenReturn(java.util.List.of(scheduled));
        when(noticeRepository.findExpirableNoticesForUpdate(any(), org.mockito.ArgumentMatchers.eq(lifecycleNow)))
                .thenReturn(java.util.List.of(expiring));

        int expiredCount = service.expireDueNotices(7L);

        assertThat(scheduled.getStatus()).as("시작 시각이 지난 예약 공지는 ACTIVE로 전환되어야 한다").isEqualTo(PlaceOperatingNoticeStatus.ACTIVE);
        assertThat(expiring.getStatus()).as("만료 시각에 도달한 공지는 EXPIRED로 전환되어야 한다").isEqualTo(PlaceOperatingNoticeStatus.EXPIRED);
        assertThat(expiredCount).isEqualTo(1);
        verify(outboxEventPublisher, org.mockito.Mockito.times(2)).publish(any(), any(), any(), any(), any());
    }

    @Test
    void merchantCanListAllNoticeStatusesWithTimestamps() {
        MapPlace place = place();
        PlaceOperatingNotice active = PlaceOperatingNotice.create(
                place, PlaceOperatingNoticeType.GENERAL, PlaceOperatingNoticeSeverity.INFO,
                "현재 운영 안내", NOW.minusHours(1), NOW.plusHours(1), 1L, NOW.minusHours(1));
        PlaceOperatingNotice scheduled = PlaceOperatingNotice.create(
                place, PlaceOperatingNoticeType.GENERAL, PlaceOperatingNoticeSeverity.INFO,
                "예약 운영 안내", NOW.plusHours(1), NOW.plusHours(2), 1L, NOW);
        PlaceOperatingNotice expired = PlaceOperatingNotice.create(
                place, PlaceOperatingNoticeType.GENERAL, PlaceOperatingNoticeSeverity.INFO,
                "종료 운영 안내", NOW.minusHours(2), NOW.minusHours(1), 1L, NOW.minusHours(2));
        expired.expire(NOW);
        PlaceOperatingNotice canceled = PlaceOperatingNotice.create(
                place, PlaceOperatingNoticeType.GENERAL, PlaceOperatingNoticeSeverity.INFO,
                "취소 운영 안내", NOW, NOW.plusHours(1), 1L, NOW);
        canceled.cancel(1L, "일정 변경", NOW.plusMinutes(1));
        when(placeRepository.findById(10L)).thenReturn(Optional.of(place));
        when(noticeRepository.findAllByPlace_IdOrderByStartsAtAscIdAsc(10L))
                .thenReturn(List.of(active, scheduled, expired, canceled));
        when(hoursEvaluator.evaluate(place)).thenReturn(new PlaceCurrentOperatingState(true, NOW));

        var response = service.listByMerchant(1L, 10L);

        assertThat(response.notices()).hasSize(4);
        assertThat(response.notices()).extracting(PlaceOperatingNoticeResponse::status)
                .containsExactly(
                        PlaceOperatingNoticeStatus.ACTIVE,
                        PlaceOperatingNoticeStatus.SCHEDULED,
                        PlaceOperatingNoticeStatus.EXPIRED,
                        PlaceOperatingNoticeStatus.CANCELED
                );
        assertThat(response.notices().get(0).createdAt()).isEqualTo(NOW.minusHours(1));
        assertThat(response.notices().get(0).updatedAt()).isEqualTo(NOW.minusHours(1));
        verify(merchantPlaceCapabilityPolicy).require(1L, 10L, MerchantPlaceCapability.OPERATING_NOTICE_MANAGE);
    }

    @Test
    void merchantWithoutNoticePermissionCannotListNotices() {
        MapPlace place = place();
        when(placeRepository.findById(10L)).thenReturn(Optional.of(place));
        doThrow(new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_PERMISSION_REQUIRED))
                .when(merchantPlaceCapabilityPolicy)
                .require(99L, 10L, MerchantPlaceCapability.OPERATING_NOTICE_MANAGE);

        assertThatThrownBy(() -> service.listByMerchant(99L, 10L))
                .isInstanceOf(MerchantOwnerException.class);
        verify(noticeRepository, never()).findAllByPlace_IdOrderByStartsAtAscIdAsc(10L);
    }

    private PlaceOperatingNoticeCreateRequest request() {
        return new PlaceOperatingNoticeCreateRequest(
                PlaceOperatingNoticeType.GENERAL,
                PlaceOperatingNoticeSeverity.INFO,
                "오늘 정상 운영합니다.",
                NOW,
                NOW.plusHours(2)
        );
    }

    private MapPlace place() {
        return MapPlace.builder().id(10L).name("테스트 상점").address("서울시 테스트로 1")
                .latitude(37.5d).longitude(127.0d).userId(1L).registrant("merchant").build();
    }

    private void fixedClock() {
        when(clock.instant()).thenReturn(NOW.toInstant(ZoneOffset.UTC));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }
}
