package com.typenull.pingdom.place.application.service.place.information;

import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationDisputeCreateRequest;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationDisputeResponse;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationDisputeReviewRequest;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationReportCreateRequest;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationReportPageResponse;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationReportResponse;
import com.typenull.pingdom.place.api.dto.place.information.report.PlaceInformationReportReviewRequest;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationEvidence;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationDisputeStatus;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReport;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportDispute;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceInformationEvidenceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceInformationReportDisputeRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceInformationReportRepository;
import com.typenull.pingdom.place.outbox.information.PlaceInformationDisputeOutboxPayload;
import com.typenull.pingdom.place.outbox.information.PlaceInformationReportOutboxPayload;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.observability.PlaceInformationMetrics;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceInformationReportService {

    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceInformationEvidenceRepository placeInformationEvidenceRepository;
    private final PlaceInformationReportRepository placeInformationReportRepository;
    private final PlaceInformationReportDisputeRepository placeInformationReportDisputeRepository;
    private final MerchantOwnerPlaceRepository merchantOwnerPlaceRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final AdminAuditLogService adminAuditLogService;
    private final PlaceInformationMetrics placeInformationMetrics;
    private final Clock clock;

    @Transactional
    public PlaceInformationReportResponse submit(Long userId, Long placeId, PlaceInformationReportCreateRequest request) {
        MapPlace place = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
        PlaceInformationEvidence evidence = findEvidence(placeId, request.evidenceId());
        if (placeInformationReportRepository.existsByReporterUserIdAndPlace_IdAndTargetTypeAndStatus(
                userId,
                placeId,
                request.targetType(),
                PlaceInformationReportStatus.SUBMITTED
        )) {
            throw new MapException(MapErrorCode.PLACE_INFORMATION_REPORT_ALREADY_SUBMITTED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            PlaceInformationReport report = placeInformationReportRepository.saveAndFlush(PlaceInformationReport.submit(
                    place,
                    evidence,
                    userId,
                    request.targetType(),
                    request.reasonType(),
                    request.description(),
                    request.evidenceUrl(),
                    now
            ));
            placeInformationMetrics.recordReportSubmitted(report.getTargetType());
            publishReportEvent(OutboxEventType.PLACE_INFORMATION_REPORT_SUBMITTED, report, now);
            return PlaceInformationReportResponse.from(report);
        } catch (IllegalArgumentException exception) {
            throw new MapException(MapErrorCode.PLACE_INFORMATION_REPORT_INVALID_REQUEST);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_place_information_report_active")) {
                throw new MapException(MapErrorCode.PLACE_INFORMATION_REPORT_ALREADY_SUBMITTED);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public PlaceInformationReportPageResponse listMine(Long userId, int page, int limit) {
        Page<PlaceInformationReport> reports = placeInformationReportRepository.findAllByReporterUserId(
                userId,
                pageRequest(page, limit)
        );
        return page(reports, page, limit);
    }

    @Transactional(readOnly = true)
    public PlaceInformationReportResponse getMine(Long userId, Long reportId) {
        PlaceInformationReport report = findReport(reportId);
        if (!report.getReporterUserId().equals(userId) && !canManagePlace(userId, report.getPlace())) {
            throw new MapException(MapErrorCode.PLACE_INFORMATION_REPORT_FORBIDDEN);
        }
        return PlaceInformationReportResponse.from(report);
    }

    @Transactional
    public PlaceInformationDisputeResponse submitDispute(
            Long userId,
            Long reportId,
            PlaceInformationDisputeCreateRequest request
    ) {
        PlaceInformationReport report = placeInformationReportRepository.findWithLockById(reportId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_INFORMATION_REPORT_NOT_FOUND));
        if (!canManagePlace(userId, report.getPlace())) {
            throw new MapException(MapErrorCode.PLACE_INFORMATION_DISPUTE_FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        PlaceInformationReportStatus beforeStatus = report.getStatus();
        try {
            PlaceInformationReportDispute dispute = report.submitDispute(
                    userId,
                    request.description(),
                    request.evidenceUrl(),
                    now
            );
            placeInformationMetrics.recordDisputeSubmitted();
            placeInformationMetrics.recordReportStatusUpdate(beforeStatus, report.getStatus());
            publishDisputeEvent(OutboxEventType.PLACE_INFORMATION_DISPUTE_SUBMITTED, dispute, now);
            publishReportEvent(OutboxEventType.PLACE_INFORMATION_REPORT_DISPUTED, report, now);
            return PlaceInformationDisputeResponse.from(dispute);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new MapException(MapErrorCode.PLACE_INFORMATION_DISPUTE_INVALID_REQUEST);
        }
    }

    @Transactional(readOnly = true)
    public PlaceInformationReportPageResponse listForAdmin(PlaceInformationReportStatus status, int page, int limit) {
        Page<PlaceInformationReport> reports = status == null
                ? placeInformationReportRepository.findAll(pageRequest(page, limit))
                : placeInformationReportRepository.findAllByStatus(status, pageRequest(page, limit));
        return page(reports, page, limit);
    }

    @Transactional(readOnly = true)
    public PlaceInformationReportResponse getForAdmin(Long reportId) {
        return PlaceInformationReportResponse.from(findReport(reportId));
    }

    @Transactional
    public PlaceInformationReportResponse reviewReport(
            Long adminUserId,
            Long reportId,
            PlaceInformationReportReviewRequest request
    ) {
        PlaceInformationReport report = placeInformationReportRepository.findWithLockById(reportId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_INFORMATION_REPORT_NOT_FOUND));
        PlaceInformationReportStatus beforeStatus = report.getStatus();
        Map<String, Object> beforeState = reportState(report);
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            switch (request.status()) {
                case UNDER_REVIEW -> report.startReview(adminUserId, now);
                case ACCEPTED -> report.accept(adminUserId, request.reviewReason(), now);
                case REJECTED -> report.reject(adminUserId, request.reviewReason(), now);
                case RESOLVED -> report.resolve(adminUserId, request.reviewReason(), now);
                default -> throw new IllegalArgumentException("unsupported report review status");
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new MapException(MapErrorCode.PLACE_INFORMATION_REPORT_INVALID_REQUEST);
        }
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_INFORMATION_REPORT_REVIEWED,
                AdminAuditTargetType.PLACE_INFORMATION_REPORT,
                report.getId(),
                request.reviewReason(),
                beforeState,
                reportState(report)
        );
        placeInformationMetrics.recordReportStatusUpdate(beforeStatus, report.getStatus());
        publishReportEvent(OutboxEventType.PLACE_INFORMATION_REPORT_REVIEWED, report, now);
        return PlaceInformationReportResponse.from(report);
    }

    @Transactional
    public PlaceInformationDisputeResponse reviewDispute(
            Long adminUserId,
            Long reportId,
            Long disputeId,
            PlaceInformationDisputeReviewRequest request
    ) {
        PlaceInformationReportDispute dispute = placeInformationReportDisputeRepository
                .findWithLockByIdAndReport_Id(disputeId, reportId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_INFORMATION_DISPUTE_NOT_FOUND));
        PlaceInformationDisputeStatus beforeStatus = dispute.getStatus();
        Map<String, Object> beforeState = disputeState(dispute);
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            if (request.status() == PlaceInformationDisputeStatus.ACCEPTED) {
                dispute.accept(adminUserId, request.reviewReason(), now);
            } else if (request.status() == PlaceInformationDisputeStatus.REJECTED) {
                dispute.reject(adminUserId, request.reviewReason(), now);
            } else {
                throw new IllegalArgumentException("unsupported dispute review status");
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new MapException(MapErrorCode.PLACE_INFORMATION_DISPUTE_INVALID_REQUEST);
        }
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_INFORMATION_DISPUTE_REVIEWED,
                AdminAuditTargetType.PLACE_INFORMATION_DISPUTE,
                dispute.getId(),
                request.reviewReason(),
                beforeState,
                disputeState(dispute)
        );
        placeInformationMetrics.recordDisputeStatusUpdate(beforeStatus, dispute.getStatus());
        publishDisputeEvent(OutboxEventType.PLACE_INFORMATION_DISPUTE_REVIEWED, dispute, now);
        return PlaceInformationDisputeResponse.from(dispute);
    }

    private PlaceInformationEvidence findEvidence(Long placeId, Long evidenceId) {
        if (evidenceId == null) {
            return null;
        }
        return placeInformationEvidenceRepository.findByIdAndPlace_Id(evidenceId, placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_INFORMATION_REPORT_INVALID_REQUEST));
    }

    private PlaceInformationReport findReport(Long reportId) {
        return placeInformationReportRepository.findById(reportId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_INFORMATION_REPORT_NOT_FOUND));
    }

    private boolean canManagePlace(Long userId, MapPlace place) {
        return userId != null
                && (userId.equals(place.getUserId())
                || merchantOwnerPlaceRepository.existsByPlaceIdAndMerchantOwnerUserId(place.getId(), userId));
    }

    private PageRequest pageRequest(int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return PageRequest.of(safePage - 1, safeLimit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private PlaceInformationReportPageResponse page(Page<PlaceInformationReport> reports, int page, int limit) {
        return new PlaceInformationReportPageResponse(
                reports.getContent().stream().map(PlaceInformationReportResponse::from).toList(),
                Math.max(page, 1),
                Math.max(1, Math.min(limit, 100)),
                reports.getTotalElements(),
                reports.getTotalPages(),
                reports.hasNext()
        );
    }

    private void publishReportEvent(OutboxEventType eventType, PlaceInformationReport report, LocalDateTime occurredAt) {
        outboxEventPublisher.publish(
                "place-information-report:%s:%d:%s".formatted(eventType, report.getId(), occurredAt),
                eventType,
                new PlaceInformationReportOutboxPayload(
                        report.getPlace().getId(),
                        report.getId(),
                        report.getReporterUserId(),
                        report.getStatus(),
                        occurredAt
                ),
                "PLACE",
                String.valueOf(report.getPlace().getId())
        );
    }

    private void publishDisputeEvent(
            OutboxEventType eventType,
            PlaceInformationReportDispute dispute,
            LocalDateTime occurredAt
    ) {
        outboxEventPublisher.publish(
                "place-information-dispute:%s:%d:%s".formatted(eventType, dispute.getId(), occurredAt),
                eventType,
                new PlaceInformationDisputeOutboxPayload(
                        dispute.getReport().getPlace().getId(),
                        dispute.getReport().getId(),
                        dispute.getId(),
                        dispute.getDisputedByUserId(),
                        dispute.getStatus(),
                        occurredAt
                ),
                "PLACE",
                String.valueOf(dispute.getReport().getPlace().getId())
        );
    }

    private Map<String, Object> reportState(PlaceInformationReport report) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("reportId", report.getId());
        state.put("placeId", report.getPlace().getId());
        state.put("status", report.getStatus());
        state.put("targetType", report.getTargetType());
        state.put("reasonType", report.getReasonType());
        state.put("reviewedByAdminUserId", report.getReviewedByAdminUserId());
        state.put("reviewedAt", report.getReviewedAt());
        return state;
    }

    private Map<String, Object> disputeState(PlaceInformationReportDispute dispute) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("disputeId", dispute.getId());
        state.put("reportId", dispute.getReport().getId());
        state.put("status", dispute.getStatus());
        state.put("reviewedByAdminUserId", dispute.getReviewedByAdminUserId());
        state.put("reviewedAt", dispute.getReviewedAt());
        return state;
    }

    private boolean hasConstraint(Throwable throwable, String constraintName) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
