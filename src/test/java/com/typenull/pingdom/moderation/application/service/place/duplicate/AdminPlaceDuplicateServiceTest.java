package com.typenull.pingdom.moderation.application.service.place.duplicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeResponse;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.application.service.place.merge.AdminPlaceMergeService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.domain.place.PlaceDuplicateCandidate;
import com.typenull.pingdom.moderation.domain.place.PlaceDuplicateDecisionStatus;
import com.typenull.pingdom.moderation.domain.place.PlaceDuplicateMatchReason;
import com.typenull.pingdom.moderation.infrastructure.persistence.PlaceDuplicateCandidateRepository;
import com.typenull.pingdom.moderation.outbox.notification.AdminNotificationOutboxPublisher;
import java.math.BigDecimal;
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

@ExtendWith(MockitoExtension.class)
class AdminPlaceDuplicateServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PlaceDuplicateCandidateRepository candidateRepository;
    @Mock
    private AdminPlaceMergeService adminPlaceMergeService;
    @Mock
    private AdminAuditLogService adminAuditLogService;
    @Mock
    private AdminNotificationOutboxPublisher adminNotificationOutboxPublisher;

    private AdminPlaceDuplicateService service;

    /**
     * 중복 후보의 판정·병합 위임·감사·알림 협력을 검사할 서비스를 고정 Clock으로 구성.
     */
    @BeforeEach
    void setUp() {
        service = new AdminPlaceDuplicateService(
                candidateRepository,
                adminPlaceMergeService,
                adminAuditLogService,
                adminNotificationOutboxPublisher,
                CLOCK
        );
    }

    /**
     * 없는 장소 쌍을 역순으로 탐지하면 새 후보 ID를 응답하고 정규화된 장소 순서로 탐지 알림을 발행하는지 검증.
     */
    @Test
    void detectsCandidateAndPublishesNotification() {
        when(candidateRepository.findByLeftPlaceIdAndRightPlaceId(1L, 2L)).thenReturn(Optional.empty());
        when(candidateRepository.save(any())).thenAnswer(invocation -> {
            PlaceDuplicateCandidate candidate = invocation.getArgument(0);
            ReflectionTestUtils.setField(candidate, "id", 11L);
            return candidate;
        });

        var response = service.detect(
                2L,
                1L,
                PlaceDuplicateMatchReason.NAME_ADDRESS_COORDINATE,
                new BigDecimal("0.9500"),
                12
        );

        assertThat(response.candidateId()).isEqualTo(11L);
        verify(adminNotificationOutboxPublisher).publishDuplicatePlaceDetected(11L, 1L, 2L);
    }

    /**
     * 잠금 조회한 대기 후보를 확인하면 CONFIRMED 상태·심사자를 응답하고 후보 대상 감사 기록을 남기는지 검증.
     */
    @Test
    void confirmsLockedCandidateWithAudit() {
        PlaceDuplicateCandidate candidate = candidate();
        when(candidateRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(candidate));

        var response = service.confirm(7L, 10L, "동일 장소 확인");

        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.reviewedByAdminUserId()).isEqualTo(7L);
        verify(adminAuditLogService).record(
                eq(7L),
                eq(AdminAuditAction.PLACE_DUPLICATE_CONFIRMED),
                eq(AdminAuditTargetType.PLACE_DUPLICATE_CANDIDATE),
                eq(10L),
                eq("동일 장소 확인"),
                any(),
                any()
        );
    }

    /**
     * 이미 기각된 후보를 다시 확인하면 판정 완료 오류를 반환하고 감사 기록을 추가하지 않는지 검증.
     */
    @Test
    void rejectsRepeatedCandidateDecision() {
        PlaceDuplicateCandidate candidate = candidate();
        candidate.reject(7L, "서로 다른 장소", LocalDateTime.now(CLOCK));
        when(candidateRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service.confirm(8L, 10L, "재판정"))
                .isInstanceOfSatisfying(AdminException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AdminErrorCode.PLACE_DUPLICATE_DECISION_ALREADY_COMPLETED));

        verify(adminAuditLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * 확인된 후보에서 유지할 장소를 지정하면 반대편 장소를 source로 삼고 후보 ID를 포함한 병합 요청을 위임하는지 검증.
     */
    @Test
    void mergeUsesConfirmedCandidatePair() {
        PlaceDuplicateCandidate candidate = candidate();
        candidate.confirm(7L, "동일 장소 확인", LocalDateTime.now(CLOCK));
        when(candidateRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(candidate));
        when(adminPlaceMergeService.mergePlaces(eq(7L), any()))
                .thenReturn(new AdminMapPlaceMergeResponse(2L, 1L, "중복 장소를 병합했습니다."));

        service.merge(7L, 10L, 1L);

        ArgumentCaptor<com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeRequest> captor =
                ArgumentCaptor.forClass(
                        com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeRequest.class
                );
        verify(adminPlaceMergeService).mergePlaces(eq(7L), captor.capture());
        assertThat(captor.getValue().sourcePlaceId()).isEqualTo(2L);
        assertThat(captor.getValue().targetPlaceId()).isEqualTo(1L);
        assertThat(captor.getValue().candidateId()).isEqualTo(10L);
    }

    /**
     * 대기 후보는 병합 불가 오류로 거절하고 실제 병합 서비스를 호출하지 않는지 검증.
     */
    @Test
    void pendingCandidateCannotBeMerged() {
        when(candidateRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(candidate()));

        assertThatThrownBy(() -> service.merge(7L, 10L, 1L))
                .isInstanceOfSatisfying(AdminException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AdminErrorCode.PLACE_MERGE_NOT_ALLOWED));

        verify(adminPlaceMergeService, never()).mergePlaces(any(), any());
    }

    /**
     * 역순 장소 쌍을 정규화한 ID 10의 대기 후보를 만들고 초기 상태도 확인.
     */
    private PlaceDuplicateCandidate candidate() {
        PlaceDuplicateCandidate candidate = PlaceDuplicateCandidate.detect(
                2L,
                1L,
                PlaceDuplicateMatchReason.NAME_ADDRESS_COORDINATE,
                new BigDecimal("0.9500"),
                12,
                LocalDateTime.now(CLOCK)
        );
        ReflectionTestUtils.setField(candidate, "id", 10L);
        assertThat(candidate.getStatus()).isEqualTo(PlaceDuplicateDecisionStatus.PENDING);
        return candidate;
    }
}
