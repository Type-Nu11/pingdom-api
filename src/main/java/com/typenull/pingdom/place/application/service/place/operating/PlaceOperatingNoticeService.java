package com.typenull.pingdom.place.application.service.place.operating;

import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceCapability;
import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceCapabilityPolicy;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeCancelRequest;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeCreateRequest;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeListResponse;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeResponse;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeUpdateRequest;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNotice;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceOperatingNoticeRepository;
import com.typenull.pingdom.place.outbox.operating.PlaceOperatingNoticeOutboxPayload;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.observability.PlaceOperatingNoticeMetrics;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 운영 공지의 작성·수정·취소와 예약 공개·만료 상태 전이를 조정합니다.
 * 조회는 기준 시각의 노출 여부를 계산하고 변경은 outbox와 지표, 관리자 변경은 감사 로그에 반영합니다.
 */
@Service
@RequiredArgsConstructor
public class PlaceOperatingNoticeService {

    private static final Set<PlaceOperatingNoticeStatus> NON_TERMINAL_STATUSES = Set.of(
            PlaceOperatingNoticeStatus.SCHEDULED,
            PlaceOperatingNoticeStatus.ACTIVE
    );

    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceOperatingNoticeRepository placeOperatingNoticeRepository;
    private final MerchantOwnerPlaceRepository merchantOwnerPlaceRepository;
    private final MerchantPlaceCapabilityPolicy merchantPlaceCapabilityPolicy;
    private final OutboxEventPublisher outboxEventPublisher;
    private final AdminAuditLogService adminAuditLogService;
    private final PlaceOperatingNoticeMetrics placeOperatingNoticeMetrics;
    private final PlaceOperatingHoursEvaluator operatingHoursEvaluator;
    private final Clock clock;

    /**
     * 장소의 영업 판단 시각에 노출 가능한 미종결 공지만 시작 시각·ID 순으로 반환합니다.
     * 응답에는 현재 영업 여부와 동일 확인 시각을 포함하며 장소 부재는 거절합니다. 장소 공개 상태를 별도로 검사하지는 않습니다.
     */
    @Transactional(readOnly = true)
    public PlaceOperatingNoticeListResponse listActive(Long placeId) {
        MapPlace place = findPlace(placeId);
        PlaceCurrentOperatingState operatingState = operatingHoursEvaluator.evaluate(place);
        LocalDateTime checkedAt = operatingState.checkedAt();
        List<PlaceOperatingNoticeResponse> notices = placeOperatingNoticeRepository
                .findAllByPlace_IdAndStatusInOrderByStartsAtAscIdAsc(placeId, NON_TERMINAL_STATUSES)
                .stream()
                .filter(notice -> notice.isVisibleAt(checkedAt))
                .map(notice -> PlaceOperatingNoticeResponse.from(notice, checkedAt))
                .toList();
        return new PlaceOperatingNoticeListResponse(
                placeId,
                operatingState.currentlyOperating(),
                checkedAt,
                notices
        );
    }

    /**
     * OPERATING_NOTICE_MANAGE 권한을 확인한 뒤 종료·취소를 포함한 장소 공지 전체를 시작 시각·ID 순으로 반환합니다.
     * 현재 영업 여부와 각 공지 상태는 같은 확인 시각으로 계산합니다.
     */
    @Transactional(readOnly = true)
    public PlaceOperatingNoticeListResponse listByMerchant(Long userId, Long placeId) {
        MapPlace place = findPlace(placeId);
        merchantPlaceCapabilityPolicy.require(userId, placeId, MerchantPlaceCapability.OPERATING_NOTICE_MANAGE);
        PlaceCurrentOperatingState operatingState = operatingHoursEvaluator.evaluate(place);
        LocalDateTime checkedAt = operatingState.checkedAt();
        List<PlaceOperatingNoticeResponse> notices = placeOperatingNoticeRepository
                .findAllByPlace_IdOrderByStartsAtAscIdAsc(placeId)
                .stream()
                .map(notice -> PlaceOperatingNoticeResponse.from(notice, checkedAt))
                .toList();
        return new PlaceOperatingNoticeListResponse(
                placeId,
                operatingState.currentlyOperating(),
                checkedAt,
                notices
        );
    }

    /**
     * 현재 장소 소유 연결을 확인하고 유형·기간·내용이 유효한 운영 공지를 생성하여 응답합니다.
     * 접수 지표와 outbox를 기록하며 같은 유형의 활성 공지 유일 제약 위반은 이미 활성인 공지 오류로 변환합니다.
     */
    @Transactional
    public PlaceOperatingNoticeResponse createByMerchant(
            Long userId,
            Long placeId,
            PlaceOperatingNoticeCreateRequest request
    ) {
        MapPlace place = findPlace(placeId);
        ensureMerchantCanManage(userId, place);
        return create(userId, place, request, false);
    }

    @Transactional
    public PlaceOperatingNoticeResponse createByAdmin(
            Long adminUserId,
            Long placeId,
            PlaceOperatingNoticeCreateRequest request
    ) {
        MapPlace place = findPlace(placeId);
        return create(adminUserId, place, request, true);
    }

    /**
     * 공지 행을 잠가 경로의 장소와 현재 소유권을 확인한 뒤 심각도·내용을 수정하고 outbox를 기록합니다.
     * 허용되지 않는 상태·입력은 거절하며 이 경로에서는 관리자 감사 기록을 생성하지 않습니다.
     */
    @Transactional
    public PlaceOperatingNoticeResponse updateByMerchant(
            Long userId,
            Long placeId,
            Long noticeId,
            PlaceOperatingNoticeUpdateRequest request
    ) {
        PlaceOperatingNotice notice = findNotice(noticeId);
        ensureSamePlace(placeId, notice);
        ensureMerchantCanManage(userId, notice.getPlace());
        return update(userId, notice, request, false);
    }

    /**
     * 공지 행을 잠가 경로의 장소와 일치하는지 확인하고 심각도·내용 수정과 outbox·감사 기록을 저장합니다.
     * 관리자 권한은 호출 경계에서 검증해야 하며 허용되지 않는 상태·입력은 공지 요청 오류로 변환합니다.
     */
    @Transactional
    public PlaceOperatingNoticeResponse updateByAdmin(
            Long adminUserId,
            Long placeId,
            Long noticeId,
            PlaceOperatingNoticeUpdateRequest request
    ) {
        PlaceOperatingNotice notice = findNotice(noticeId);
        ensureSamePlace(placeId, notice);
        return update(adminUserId, notice, request, true);
    }

    /**
     * 공지 행을 잠가 경로의 장소와 현재 소유권을 확인하고 취소 사유와 함께 취소 상태로 전이합니다.
     * 상태 지표·outbox를 기록하고 변경 결과를 반환하며 도메인이 거절한 상태·사유는 공지 요청 오류로 변환합니다.
     */
    @Transactional
    public PlaceOperatingNoticeResponse cancelByMerchant(
            Long userId,
            Long placeId,
            Long noticeId,
            PlaceOperatingNoticeCancelRequest request
    ) {
        PlaceOperatingNotice notice = findNotice(noticeId);
        ensureSamePlace(placeId, notice);
        ensureMerchantCanManage(userId, notice.getPlace());
        return cancel(userId, notice, request, false);
    }

    /**
     * 공지 행을 잠가 경로의 장소와 일치하는지 확인한 뒤 취소 상태·사유를 반영하고 지표·outbox·관리자 감사 기록을 남깁니다.
     * 관리자 권한은 호출 경계의 책임이며 허용되지 않는 상태·사유는 거절합니다.
     */
    @Transactional
    public PlaceOperatingNoticeResponse cancelByAdmin(
            Long adminUserId,
            Long placeId,
            Long noticeId,
            PlaceOperatingNoticeCancelRequest request
    ) {
        PlaceOperatingNotice notice = findNotice(noticeId);
        ensureSamePlace(placeId, notice);
        return cancel(adminUserId, notice, request, true);
    }

    /**
     * 예약 공지 중 공개 시각이 된 항목을 활성화한 뒤 종료 시각이 지난 비종결 공지를 만료시킵니다.
     * 반환값은 활성화 건수를 제외한 만료 건수입니다.
     */
    @Transactional
    public int expireDueNotices(Long adminUserId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<PlaceOperatingNotice> activatableNotices = placeOperatingNoticeRepository.findActivatableNoticesForUpdate(
                PlaceOperatingNoticeStatus.SCHEDULED,
                now
        );
        for (PlaceOperatingNotice notice : activatableNotices) {
            PlaceOperatingNoticeStatus beforeStatus = notice.getStatus();
            notice.activate(now);
            placeOperatingNoticeMetrics.recordStatusUpdate(notice.getNoticeType(), beforeStatus, notice.getStatus());
            publish(OutboxEventType.PLACE_OPERATING_NOTICE_UPDATED, notice, now);
            adminAuditLogService.record(
                    adminUserId,
                    AdminAuditAction.PLACE_OPERATING_NOTICE_UPDATED,
                    AdminAuditTargetType.PLACE_OPERATING_NOTICE,
                    notice.getId(),
                    "activated by operating notice lifecycle command",
                    beforeStatus,
                    notice.getStatus()
            );
        }

        List<PlaceOperatingNotice> notices = placeOperatingNoticeRepository.findExpirableNoticesForUpdate(
                NON_TERMINAL_STATUSES,
                now
        );
        for (PlaceOperatingNotice notice : notices) {
            PlaceOperatingNoticeStatus beforeStatus = notice.getStatus();
            notice.expire(now);
            placeOperatingNoticeMetrics.recordStatusUpdate(notice.getNoticeType(), beforeStatus, notice.getStatus());
            publish(OutboxEventType.PLACE_OPERATING_NOTICE_EXPIRED, notice, now);
            adminAuditLogService.record(
                    adminUserId,
                    AdminAuditAction.PLACE_OPERATING_NOTICE_EXPIRED,
                    AdminAuditTargetType.PLACE_OPERATING_NOTICE,
                    notice.getId(),
                    "expired by operating notice expiration command",
                    beforeStatus,
                    notice.getStatus()
            );
        }
        return notices.size();
    }

    private PlaceOperatingNoticeResponse create(
            Long actorUserId,
            MapPlace place,
            PlaceOperatingNoticeCreateRequest request,
            boolean adminAction
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            PlaceOperatingNotice notice = placeOperatingNoticeRepository.saveAndFlush(PlaceOperatingNotice.create(
                    place,
                    request.noticeType(),
                    request.severity(),
                    request.message(),
                    request.startsAt(),
                    request.expiresAt(),
                    actorUserId,
                    now
            ));
            placeOperatingNoticeMetrics.recordCreated(notice.getNoticeType(), notice.getStatus());
            publish(OutboxEventType.PLACE_OPERATING_NOTICE_CREATED, notice, now);
            if (adminAction) {
                audit(actorUserId, AdminAuditAction.PLACE_OPERATING_NOTICE_CREATED, notice, null, notice.getStatus());
            }
            return PlaceOperatingNoticeResponse.from(notice, now);
        } catch (IllegalArgumentException exception) {
            throw new MapException(MapErrorCode.PLACE_OPERATING_NOTICE_INVALID_REQUEST);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_place_operating_notice_active_type")) {
                throw new MapException(MapErrorCode.PLACE_OPERATING_NOTICE_ALREADY_ACTIVE);
            }
            throw exception;
        }
    }

    private PlaceOperatingNoticeResponse update(
            Long actorUserId,
            PlaceOperatingNotice notice,
            PlaceOperatingNoticeUpdateRequest request,
            boolean adminAction
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            notice.updateContent(request.severity(), request.message(), actorUserId, now);
            publish(OutboxEventType.PLACE_OPERATING_NOTICE_UPDATED, notice, now);
            if (adminAction) {
                audit(actorUserId, AdminAuditAction.PLACE_OPERATING_NOTICE_UPDATED, notice, null, notice.getStatus());
            }
            return PlaceOperatingNoticeResponse.from(notice, now);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new MapException(MapErrorCode.PLACE_OPERATING_NOTICE_INVALID_REQUEST);
        }
    }

    private PlaceOperatingNoticeResponse cancel(
            Long actorUserId,
            PlaceOperatingNotice notice,
            PlaceOperatingNoticeCancelRequest request,
            boolean adminAction
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        PlaceOperatingNoticeStatus beforeStatus = notice.getStatus();
        try {
            notice.cancel(actorUserId, request.cancelReason(), now);
            placeOperatingNoticeMetrics.recordStatusUpdate(notice.getNoticeType(), beforeStatus, notice.getStatus());
            publish(OutboxEventType.PLACE_OPERATING_NOTICE_CANCELED, notice, now);
            if (adminAction) {
                audit(actorUserId, AdminAuditAction.PLACE_OPERATING_NOTICE_CANCELED, notice, beforeStatus, notice.getStatus());
            }
            return PlaceOperatingNoticeResponse.from(notice, now);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new MapException(MapErrorCode.PLACE_OPERATING_NOTICE_INVALID_REQUEST);
        }
    }

    private MapPlace findPlace(Long placeId) {
        return mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
    }

    private PlaceOperatingNotice findNotice(Long noticeId) {
        return placeOperatingNoticeRepository.findByIdForUpdate(noticeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_OPERATING_NOTICE_NOT_FOUND));
    }

    private void ensureSamePlace(Long placeId, PlaceOperatingNotice notice) {
        if (!notice.getPlace().getId().equals(placeId)) {
            throw new MapException(MapErrorCode.PLACE_OPERATING_NOTICE_NOT_FOUND);
        }
    }

    private void ensureMerchantCanManage(Long userId, MapPlace place) {
        if (userId == null || !merchantOwnerPlaceRepository.existsByPlaceIdAndMerchantOwnerUserId(place.getId(), userId)) {
            throw new MapException(MapErrorCode.PLACE_OPERATING_NOTICE_FORBIDDEN);
        }
    }

    private void audit(
            Long actorUserId,
            AdminAuditAction action,
            PlaceOperatingNotice notice,
            Object beforeState,
            Object afterState
    ) {
        adminAuditLogService.record(
                actorUserId,
                action,
                AdminAuditTargetType.PLACE_OPERATING_NOTICE,
                notice.getId(),
                "managed operating notice",
                beforeState,
                afterState
        );
    }

    private void publish(OutboxEventType eventType, PlaceOperatingNotice notice, LocalDateTime occurredAt) {
        outboxEventPublisher.publish(
                "place-operating-notice:%s:%s:%s".formatted(notice.getId(), eventType.name(), occurredAt),
                eventType,
                PlaceOperatingNoticeOutboxPayload.from(notice, occurredAt),
                "PLACE_OPERATING_NOTICE",
                String.valueOf(notice.getId())
        );
    }

    private boolean hasConstraint(Throwable exception, String constraintName) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && constraintName.equals(constraintViolation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
