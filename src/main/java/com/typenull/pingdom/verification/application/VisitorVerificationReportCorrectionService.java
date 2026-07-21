package com.typenull.pingdom.verification.application;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.shared.observability.VisitorVerificationReportMetrics;
import com.typenull.pingdom.verification.api.dto.MyVisitorVerificationReportCorrectionPageResponse;
import com.typenull.pingdom.verification.api.dto.MyVisitorVerificationReportCorrectionResponse;
import com.typenull.pingdom.verification.api.dto.VisitorVerificationReportCorrectionPageResponse;
import com.typenull.pingdom.verification.api.dto.VisitorVerificationReportCorrectionRequest;
import com.typenull.pingdom.verification.api.dto.VisitorVerificationReportCorrectionResponse;
import com.typenull.pingdom.verification.api.dto.VisitorVerificationReportCorrectionReviewRequest;
import com.typenull.pingdom.verification.domain.VisitorVerificationReport;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrection;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrectionStatus;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportStatus;
import com.typenull.pingdom.verification.domain.exception.VisitorVerificationErrorCode;
import com.typenull.pingdom.verification.domain.exception.VisitorVerificationException;
import com.typenull.pingdom.verification.infrastructure.VisitorVerificationReportCorrectionRepository;
import com.typenull.pingdom.verification.infrastructure.VisitorVerificationReportRepository;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class VisitorVerificationReportCorrectionService {

    private final VisitorVerificationReportRepository reportRepository;
    private final VisitorVerificationReportCorrectionRepository correctionRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final AdminAuditLogService adminAuditLogService;
    private final VisitorVerificationReportMetrics metrics;

    @Transactional
    public MyVisitorVerificationReportCorrectionResponse submit(
            Long userId,
            Long reportId,
            VisitorVerificationReportCorrectionRequest request
    ) {
        requireTourist(userId);
        VisitorVerificationReport report = findReportForUpdate(reportId);
        if (!report.getReporterUserId().equals(userId)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.REPORT_FORBIDDEN);
        }
        if (!report.canBeCorrected()) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.CORRECTION_NOT_ALLOWED);
        }
        if (correctionRepository.existsByReport_IdAndStatus(
                reportId,
                VisitorVerificationReportCorrectionStatus.SUBMITTED
        )) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.ACTIVE_CORRECTION_ALREADY_EXISTS);
        }

        VisitorVerificationReportCorrection correction;
        try {
            correction = VisitorVerificationReportCorrection.submit(
                    report,
                    userId,
                    request.description(),
                    request.evidenceUrl(),
                    request.waitTimeMinutes(),
                    request.languageCode(),
                    request.couponUsageStatus(),
                    request.crowdLevel(),
                    LocalDateTime.now(clock)
            );
        } catch (IllegalStateException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.CORRECTION_NOT_ALLOWED);
        } catch (IllegalArgumentException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.INVALID_CORRECTION_DETAILS);
        }

        VisitorVerificationReportCorrection saved;
        try {
            saved = correctionRepository.saveAndFlush(correction);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_visitor_verification_report_correction_active")) {
                throw new VisitorVerificationException(VisitorVerificationErrorCode.ACTIVE_CORRECTION_ALREADY_EXISTS);
            }
            throw exception;
        }
        afterCommit(metrics::recordCorrectionSubmitted);
        return MyVisitorVerificationReportCorrectionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public MyVisitorVerificationReportCorrectionPageResponse listMine(
            Long userId,
            Long reportId,
            int page,
            int limit
    ) {
        requireTourist(userId);
        VisitorVerificationReport report = findReport(reportId);
        if (!report.getReporterUserId().equals(userId)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.CORRECTION_FORBIDDEN);
        }

        Page<VisitorVerificationReportCorrection> corrections = correctionRepository
                .findAllByReport_IdAndRequesterUserId(reportId, userId, pageRequest(page, limit));
        return new MyVisitorVerificationReportCorrectionPageResponse(
                corrections.getContent().stream()
                        .map(MyVisitorVerificationReportCorrectionResponse::from)
                        .toList(),
                page,
                limit,
                corrections.getTotalElements(),
                corrections.getTotalPages(),
                corrections.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public VisitorVerificationReportCorrectionPageResponse listForAdmin(
            Long adminUserId,
            VisitorVerificationReportCorrectionStatus status,
            int page,
            int limit
    ) {
        requireAdmin(adminUserId);
        PageRequest pageable = pageRequest(page, limit);
        Page<VisitorVerificationReportCorrection> corrections = status == null
                ? correctionRepository.findAll(pageable)
                : correctionRepository.findAllByStatus(status, pageable);
        return new VisitorVerificationReportCorrectionPageResponse(
                corrections.getContent().stream()
                        .map(VisitorVerificationReportCorrectionResponse::from)
                        .toList(),
                page,
                limit,
                corrections.getTotalElements(),
                corrections.getTotalPages(),
                corrections.hasNext()
        );
    }

    @Transactional
    public VisitorVerificationReportCorrectionResponse review(
            Long adminUserId,
            Long correctionId,
            VisitorVerificationReportCorrectionReviewRequest request
    ) {
        requireAdmin(adminUserId);
        VisitorVerificationReportCorrection correction = correctionRepository.findByIdForUpdate(correctionId)
                .orElseThrow(() -> new VisitorVerificationException(
                        VisitorVerificationErrorCode.CORRECTION_NOT_FOUND
                ));
        VisitorVerificationReport report = findReportForUpdate(correction.getReport().getId());

        VisitorVerificationReportCorrectionStatus fromStatus = correction.getStatus();
        VisitorVerificationReportStatus reportStatusBefore = report.getStatus();
        Map<String, Object> beforeState = correctionState(correction, report);
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            correction.review(adminUserId, request.decision().toStatus(), request.reviewNote(), now);
            if (correction.getStatus() == VisitorVerificationReportCorrectionStatus.ACCEPTED) {
                if (hasOtherActiveReport(report)) {
                    throw new VisitorVerificationException(
                            VisitorVerificationErrorCode.ACTIVE_REPORT_ALREADY_EXISTS
                    );
                }
                report.applyCorrection(
                        correction.getDescription(),
                        correction.getEvidenceUrl(),
                        correction.getWaitTimeMinutes(),
                        correction.getLanguageCode(),
                        correction.getCouponUsageStatus(),
                        correction.getCrowdLevel(),
                        now
                );
            }
        } catch (IllegalStateException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.CORRECTION_NOT_ALLOWED);
        } catch (IllegalArgumentException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.INVALID_CORRECTION_REVIEW);
        }

        try {
            correctionRepository.saveAndFlush(correction);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_visitor_verification_report_active")) {
                throw new VisitorVerificationException(VisitorVerificationErrorCode.ACTIVE_REPORT_ALREADY_EXISTS);
            }
            throw exception;
        }
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.VISITOR_VERIFICATION_REPORT_CORRECTION_REVIEWED,
                AdminAuditTargetType.VISITOR_VERIFICATION_REPORT_CORRECTION,
                correction.getId(),
                correction.getReviewNote(),
                beforeState,
                correctionState(correction, report)
        );
        VisitorVerificationReportCorrectionStatus toStatus = correction.getStatus();
        VisitorVerificationReportStatus reportStatusAfter = report.getStatus();
        afterCommit(() -> {
            metrics.recordCorrectionStatusUpdate(fromStatus, toStatus);
            if (toStatus == VisitorVerificationReportCorrectionStatus.ACCEPTED) {
                metrics.recordReportStatusUpdate(reportStatusBefore, reportStatusAfter);
            }
        });
        return VisitorVerificationReportCorrectionResponse.from(correction);
    }

    private void requireTourist(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null || user.getRole() != UserRole.USER || user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.TOURIST_ACCOUNT_REQUIRED);
        }
    }

    private void requireAdmin(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null || user.getRole() != UserRole.ADMIN || user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.ADMIN_ACCOUNT_REQUIRED);
        }
    }

    private VisitorVerificationReport findReport(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new VisitorVerificationException(VisitorVerificationErrorCode.REPORT_NOT_FOUND));
    }

    private VisitorVerificationReport findReportForUpdate(Long reportId) {
        return reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new VisitorVerificationException(VisitorVerificationErrorCode.REPORT_NOT_FOUND));
    }

    private PageRequest pageRequest(int page, int limit) {
        return PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private boolean hasOtherActiveReport(VisitorVerificationReport report) {
        return reportRepository.existsByReporterUserIdAndPlaceIdAndReportTypeAndStatusAndIdNot(
                report.getReporterUserId(),
                report.getPlaceId(),
                report.getReportType(),
                VisitorVerificationReportStatus.SUBMITTED,
                report.getId()
        );
    }

    private Map<String, Object> correctionState(
            VisitorVerificationReportCorrection correction,
            VisitorVerificationReport report
    ) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("correctionId", correction.getId());
        state.put("status", correction.getStatus());
        state.put("reviewerAdminUserId", correction.getReviewerAdminUserId());
        state.put("reviewNote", correction.getReviewNote());
        state.put("reviewedAt", correction.getReviewedAt());
        state.put("report", reportState(report));
        return state;
    }

    private Map<String, Object> reportState(VisitorVerificationReport report) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("reportId", report.getId());
        state.put("status", report.getStatus());
        state.put("description", report.getDescription());
        state.put("evidenceUrl", report.getEvidenceUrl());
        state.put("waitTimeMinutes", report.getWaitTimeMinutes());
        state.put("languageCode", report.getLanguageCode());
        state.put("couponUsageStatus", report.getCouponUsageStatus());
        state.put("crowdLevel", report.getCrowdLevel());
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

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
