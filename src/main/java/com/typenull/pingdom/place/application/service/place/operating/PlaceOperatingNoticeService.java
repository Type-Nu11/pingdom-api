package com.typenull.pingdom.place.application.service.place.operating;

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
    private final OutboxEventPublisher outboxEventPublisher;
    private final AdminAuditLogService adminAuditLogService;
    private final PlaceOperatingNoticeMetrics placeOperatingNoticeMetrics;
    private final PlaceOperatingHoursEvaluator operatingHoursEvaluator;
    private final Clock clock;

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
        return placeOperatingNoticeRepository.findWithLockById(noticeId)
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
