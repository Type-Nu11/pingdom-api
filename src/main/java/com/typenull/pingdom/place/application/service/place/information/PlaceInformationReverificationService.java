package com.typenull.pingdom.place.application.service.place.information;

import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.place.api.dto.place.information.reverification.*;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.information.reverification.PlaceInformationReverificationRequest;
import com.typenull.pingdom.place.domain.place.information.reverification.PlaceInformationReverificationStatus;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationEvidence;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationEvidenceType;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceInformationReverificationRequestRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceInformationEvidenceRepository;
import com.typenull.pingdom.place.outbox.information.PlaceInformationReverificationOutboxPayload;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.observability.PlaceInformationMetrics;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
public class PlaceInformationReverificationService {

    private static final Set<PlaceInformationReverificationStatus> ACTIVE_STATUSES = Set.of(
            PlaceInformationReverificationStatus.REQUESTED,
            PlaceInformationReverificationStatus.RESPONDED
    );

    private final MapPlaceRepository mapPlaceRepository;
    private final MerchantOwnerPlaceRepository merchantOwnerPlaceRepository;
    private final PlaceInformationReverificationRequestRepository requestRepository;
    private final PlaceInformationEvidenceRepository evidenceRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final AdminAuditLogService adminAuditLogService;
    private final PlaceInformationMetrics metrics;
    private final Clock clock;

    @Transactional
    public PlaceInformationReverificationResponse create(
            Long adminUserId, Long placeId, PlaceInformationReverificationCreateRequest command
    ) {
        MapPlace place = mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
        MerchantOwnerPlace ownership = merchantOwnerPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_INFORMATION_REVERIFICATION_OWNER_NOT_FOUND));
        if (requestRepository.existsByPlace_IdAndStatusIn(placeId, ACTIVE_STATUSES)) {
            throw new MapException(MapErrorCode.PLACE_INFORMATION_REVERIFICATION_ALREADY_ACTIVE);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            PlaceInformationReverificationRequest request = requestRepository.saveAndFlush(
                    PlaceInformationReverificationRequest.create(
                            place, ownership.getMerchantOwnerUserId(), command.reason(), adminUserId,
                            command.dueAt(), now
                    )
            );
            publish(OutboxEventType.PLACE_INFORMATION_REVERIFICATION_REQUESTED, request, now);
            metrics.recordReverificationRequested();
            audit(adminUserId, AdminAuditAction.PLACE_INFORMATION_REVERIFICATION_REQUESTED, request, null, request.getStatus());
            return PlaceInformationReverificationResponse.from(request);
        } catch (IllegalArgumentException exception) {
            throw new MapException(MapErrorCode.PLACE_INFORMATION_REVERIFICATION_INVALID_REQUEST);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_place_information_reverification_active")) {
                throw new MapException(MapErrorCode.PLACE_INFORMATION_REVERIFICATION_ALREADY_ACTIVE);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public PlaceInformationReverificationListResponse listByPlace(Long placeId, int page, int limit) {
        if (!mapPlaceRepository.existsById(placeId)) {
            throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
        }
        return list(requestRepository.findAllByPlace_IdOrderByRequestedAtDescIdDesc(placeId, PageRequest.of(page - 1, limit)), page, limit);
    }

    @Transactional(readOnly = true)
    public PlaceInformationReverificationListResponse listMine(Long merchantUserId, int page, int limit) {
        return list(requestRepository.findAllForCurrentOwner(merchantUserId, PageRequest.of(page - 1, limit)), page, limit);
    }

    @Transactional
    public PlaceInformationReverificationResponse respond(
            Long merchantUserId, Long requestId, PlaceInformationReverificationResponseRequest command
    ) {
        PlaceInformationReverificationRequest request = findWithPlaceLock(requestId);
        if (!merchantOwnerPlaceRepository.existsByPlaceIdAndMerchantOwnerUserId(
                request.getPlace().getId(), merchantUserId)) {
            throw new MapException(MapErrorCode.PLACE_INFORMATION_REVERIFICATION_FORBIDDEN);
        }
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            if (request.isDue(now)) {
                request.expire(now);
                metrics.recordReverificationStatusUpdate(PlaceInformationReverificationStatus.REQUESTED, request.getStatus());
                return PlaceInformationReverificationResponse.from(request);
            }
            PlaceInformationEvidence evidence = PlaceInformationEvidence.submit(
                    request.getPlace(), PlaceInformationSourceType.MERCHANT_OWNER,
                    PlaceInformationEvidenceType.BUSINESS_CLAIM, null, null,
                    command.responseNote(), merchantUserId, now
            );
            evidence.markOwnerSubmitted(now);
            evidenceRepository.save(evidence);
            request.respond(merchantUserId, command.responseNote(), evidence, now);
            request.getPlace().updateInformationVerification(
                    PlaceInformationSourceType.MERCHANT_OWNER,
                    PlaceInformationVerificationStatus.OWNER_SUBMITTED,
                    null, null, now
            );
            metrics.recordReverificationStatusUpdate(PlaceInformationReverificationStatus.REQUESTED, request.getStatus());
            return PlaceInformationReverificationResponse.from(request);
        } catch (SecurityException exception) {
            throw new MapException(MapErrorCode.PLACE_INFORMATION_REVERIFICATION_FORBIDDEN);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new MapException(MapErrorCode.PLACE_INFORMATION_REVERIFICATION_INVALID_REQUEST);
        }
    }

    @Transactional
    public PlaceInformationReverificationResponse remind(Long adminUserId, Long placeId, Long requestId) {
        PlaceInformationReverificationRequest request = findWithPlaceLock(requestId);
        ensureSamePlace(placeId, request);
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            if (request.isDue(now)) {
                request.expire(now);
                metrics.recordReverificationStatusUpdate(PlaceInformationReverificationStatus.REQUESTED, request.getStatus());
                return PlaceInformationReverificationResponse.from(request);
            }
            MerchantOwnerPlace ownership = merchantOwnerPlaceRepository.findById(request.getPlace().getId())
                    .orElseThrow(() -> new MapException(MapErrorCode.PLACE_INFORMATION_REVERIFICATION_OWNER_NOT_FOUND));
            request.reassignOwner(ownership.getMerchantOwnerUserId(), now);
            request.remind(now);
            metrics.recordReverificationReminder();
            publish(OutboxEventType.PLACE_INFORMATION_REVERIFICATION_REMINDER_REQUESTED, request, now);
            audit(adminUserId, AdminAuditAction.PLACE_INFORMATION_REVERIFICATION_REMINDER_REQUESTED,
                    request, request.getReminderCount() - 1, request.getReminderCount());
            return PlaceInformationReverificationResponse.from(request);
        } catch (IllegalStateException exception) {
            throw new MapException(MapErrorCode.PLACE_INFORMATION_REVERIFICATION_INVALID_REQUEST);
        }
    }

    @Transactional
    public PlaceInformationReverificationResponse complete(Long adminUserId, Long placeId, Long requestId) {
        return finish(adminUserId, placeId, requestId, true);
    }

    @Transactional
    public PlaceInformationReverificationResponse cancel(Long adminUserId, Long placeId, Long requestId) {
        return finish(adminUserId, placeId, requestId, false);
    }

    @Transactional
    public int expireDue(Long adminUserId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<PlaceInformationReverificationRequest> due = requestRepository.findExpiredForUpdate(
                PlaceInformationReverificationStatus.REQUESTED, now, PageRequest.of(0, 100)
        );
        for (PlaceInformationReverificationRequest request : due) {
            request.expire(now);
            metrics.recordReverificationStatusUpdate(PlaceInformationReverificationStatus.REQUESTED, request.getStatus());
            audit(adminUserId, AdminAuditAction.PLACE_INFORMATION_REVERIFICATION_EXPIRED,
                    request, PlaceInformationReverificationStatus.REQUESTED, request.getStatus());
        }
        return due.size();
    }

    private PlaceInformationReverificationResponse finish(Long adminUserId, Long placeId, Long requestId, boolean complete) {
        PlaceInformationReverificationRequest request = findWithPlaceLock(requestId);
        ensureSamePlace(placeId, request);
        PlaceInformationReverificationStatus before = request.getStatus();
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            if (complete) {
                if (request.getEvidence() == null) {
                    throw new IllegalStateException("reverification evidence is required");
                }
                request.complete(now);
                request.getEvidence().verifyByAdmin(adminUserId, "PLACE_INFORMATION_REVERIFICATION_COMPLETED", now);
                request.getPlace().updateInformationVerification(
                        PlaceInformationSourceType.MERCHANT_OWNER,
                        PlaceInformationVerificationStatus.ADMIN_VERIFIED,
                        adminUserId, now, now
                );
            } else {
                request.cancel(now);
            }
            metrics.recordReverificationStatusUpdate(before, request.getStatus());
            AdminAuditAction action = complete
                    ? AdminAuditAction.PLACE_INFORMATION_REVERIFICATION_COMPLETED
                    : AdminAuditAction.PLACE_INFORMATION_REVERIFICATION_CANCELED;
            audit(adminUserId, action, request, before, request.getStatus());
            return PlaceInformationReverificationResponse.from(request);
        } catch (IllegalStateException exception) {
            throw new MapException(MapErrorCode.PLACE_INFORMATION_REVERIFICATION_INVALID_REQUEST);
        }
    }

    private PlaceInformationReverificationRequest findForUpdate(Long requestId) {
        return requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_INFORMATION_REVERIFICATION_NOT_FOUND));
    }

    private PlaceInformationReverificationRequest findWithPlaceLock(Long requestId) {
        PlaceInformationReverificationRequest snapshot = requestRepository.findById(requestId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_INFORMATION_REVERIFICATION_NOT_FOUND));
        mapPlaceRepository.findByIdForUpdate(snapshot.getPlace().getId())
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
        return findForUpdate(requestId);
    }

    private void ensureSamePlace(Long placeId, PlaceInformationReverificationRequest request) {
        if (!request.getPlace().getId().equals(placeId)) {
            throw new MapException(MapErrorCode.PLACE_INFORMATION_REVERIFICATION_NOT_FOUND);
        }
    }

    private PlaceInformationReverificationListResponse list(
            Page<PlaceInformationReverificationRequest> requests, int page, int limit
    ) {
        return new PlaceInformationReverificationListResponse(
                requests.stream().map(PlaceInformationReverificationResponse::from).toList(),
                page, limit, requests.getTotalElements(), requests.getTotalPages(), requests.hasNext()
        );
    }

    private void publish(OutboxEventType type, PlaceInformationReverificationRequest request, LocalDateTime now) {
        outboxEventPublisher.publish(
                "place-information-reverification:%d:%s:%d".formatted(request.getId(), type, request.getReminderCount()),
                type, PlaceInformationReverificationOutboxPayload.from(request, now),
                "PLACE_INFORMATION_REVERIFICATION", String.valueOf(request.getId())
        );
    }

    private void audit(Long adminUserId, AdminAuditAction action,
                       PlaceInformationReverificationRequest request, Object before, Object after) {
        adminAuditLogService.record(adminUserId, action, AdminAuditTargetType.PLACE_INFORMATION_REVERIFICATION,
                request.getId(), "managed place information reverification", before, after);
    }

    private boolean hasConstraint(Throwable exception, String constraintName) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation
                    && constraintName.equals(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
