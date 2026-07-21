package com.typenull.pingdom.place.application.service.place.information;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.place.api.dto.place.information.reverification.PlaceInformationReverificationCreateRequest;
import com.typenull.pingdom.place.api.dto.place.information.reverification.PlaceInformationReverificationResponseRequest;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.information.reverification.PlaceInformationReverificationRequest;
import com.typenull.pingdom.place.domain.place.information.reverification.PlaceInformationReverificationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceInformationReverificationRequestRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.observability.PlaceInformationMetrics;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceInformationReverificationServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 21, 12, 0);

    @Mock MapPlaceRepository placeRepository;
    @Mock MerchantOwnerPlaceRepository ownerRepository;
    @Mock PlaceInformationReverificationRequestRepository requestRepository;
    @Mock com.typenull.pingdom.place.infrastructure.persistence.place.PlaceInformationEvidenceRepository evidenceRepository;
    @Mock OutboxEventPublisher outboxPublisher;
    @Mock AdminAuditLogService auditLogService;
    @Mock PlaceInformationMetrics metrics;
    @Mock Clock clock;
    @InjectMocks PlaceInformationReverificationService service;

    @Test
    void createsRequestForCurrentPlaceOwnerWithOutboxAndAudit() {
        fixedClock();
        when(placeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(place()));
        when(ownerRepository.findById(10L)).thenReturn(Optional.of(ownership()));
        when(requestRepository.existsByPlace_IdAndStatusIn(any(), any())).thenReturn(false);
        when(requestRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(7L, 10L,
                new PlaceInformationReverificationCreateRequest("주소 재확인", NOW.plusDays(2)));

        assertThat(response.status()).isEqualTo(PlaceInformationReverificationStatus.REQUESTED);
        assertThat(response.merchantOwnerUserId()).isEqualTo(20L);
        verify(outboxPublisher).publish(any(), any(), any(), any(), any());
        verify(auditLogService).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsDuplicateActiveRequest() {
        when(placeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(place()));
        when(ownerRepository.findById(10L)).thenReturn(Optional.of(ownership()));
        when(requestRepository.existsByPlace_IdAndStatusIn(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(7L, 10L,
                new PlaceInformationReverificationCreateRequest("주소 재확인", NOW.plusDays(2))))
                .isInstanceOfSatisfying(MapException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MapErrorCode.PLACE_INFORMATION_REVERIFICATION_ALREADY_ACTIVE));
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void onlyAssignedOwnerCanRespond() {
        PlaceInformationReverificationRequest request = request();
        stubLockedRequest(request);
        when(ownerRepository.existsByPlaceIdAndMerchantOwnerUserId(10L, 21L)).thenReturn(false);

        assertThatThrownBy(() -> service.respond(21L, 1L,
                new PlaceInformationReverificationResponseRequest("확인했습니다.")))
                .isInstanceOfSatisfying(MapException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MapErrorCode.PLACE_INFORMATION_REVERIFICATION_FORBIDDEN));
        verify(outboxPublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void ownerResponseAndAdminCompletionUpdatePlaceVerification() {
        fixedClock();
        PlaceInformationReverificationRequest request = request();
        stubLockedRequest(request);
        when(ownerRepository.existsByPlaceIdAndMerchantOwnerUserId(10L, 20L)).thenReturn(true);

        service.respond(20L, 1L, new PlaceInformationReverificationResponseRequest("주소와 영업시간 확인 완료"));
        var completed = service.complete(7L, 10L, 1L);

        assertThat(completed.status()).isEqualTo(PlaceInformationReverificationStatus.COMPLETED);
        assertThat(request.getPlace().getInformationVerificationStatus())
                .isEqualTo(com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus.ADMIN_VERIFIED);
        assertThat(request.getPlace().getInformationVerifiedByAdminUserId()).isEqualTo(7L);
        verify(evidenceRepository).save(any(com.typenull.pingdom.place.domain.place.information.PlaceInformationEvidence.class));
        verify(outboxPublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void adminCannotManageRequestThroughAnotherPlacePath() {
        PlaceInformationReverificationRequest request = request();
        stubLockedRequest(request);

        assertThatThrownBy(() -> service.remind(7L, 99L, 1L))
                .isInstanceOfSatisfying(MapException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MapErrorCode.PLACE_INFORMATION_REVERIFICATION_NOT_FOUND));
        verify(outboxPublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void expiredResponseCommitsExpiredStateWithoutCreatingEvidence() {
        LocalDateTime expiredAt = NOW.plusDays(3);
        when(clock.instant()).thenReturn(expiredAt.toInstant(ZoneOffset.UTC));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        PlaceInformationReverificationRequest request = request();
        stubLockedRequest(request);
        when(ownerRepository.existsByPlaceIdAndMerchantOwnerUserId(10L, 20L)).thenReturn(true);

        var response = service.respond(20L, 1L,
                new PlaceInformationReverificationResponseRequest("기한 후 응답"));

        assertThat(response.status()).isEqualTo(PlaceInformationReverificationStatus.EXPIRED);
        verify(evidenceRepository, never()).save(any());
    }

    @Test
    void reminderIsReassignedToCurrentOwnerAfterOwnershipTransfer() {
        fixedClock();
        PlaceInformationReverificationRequest request = request();
        stubLockedRequest(request);
        when(ownerRepository.findById(10L)).thenReturn(Optional.of(
                MerchantOwnerPlace.builder().placeId(10L).merchantOwnerUserId(30L).createdAt(NOW).build()));

        var response = service.remind(7L, 10L, 1L);

        assertThat(response.merchantOwnerUserId()).isEqualTo(30L);
        verify(outboxPublisher).publish(any(), any(), any(), any(), any());
    }

    private PlaceInformationReverificationRequest request() {
        return PlaceInformationReverificationRequest.create(place(), 20L, "정보 확인", 7L, NOW.plusDays(2), NOW);
    }

    private MapPlace place() {
        return MapPlace.builder().id(10L).name("테스트 장소").address("서울시 테스트로 1")
                .latitude(37.5d).longitude(127.0d).registrant("merchant").build();
    }

    private MerchantOwnerPlace ownership() {
        return MerchantOwnerPlace.builder().placeId(10L).merchantOwnerUserId(20L).createdAt(NOW).build();
    }

    private void fixedClock() {
        when(clock.instant()).thenReturn(NOW.toInstant(ZoneOffset.UTC));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    private void stubLockedRequest(PlaceInformationReverificationRequest request) {
        when(requestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(placeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request.getPlace()));
        when(requestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(request));
    }
}
