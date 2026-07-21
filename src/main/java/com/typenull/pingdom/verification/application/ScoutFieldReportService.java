package com.typenull.pingdom.verification.application;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.observability.ScoutFieldReportMetrics;
import com.typenull.pingdom.verification.api.dto.MyScoutFieldReportPageResponse;
import com.typenull.pingdom.verification.api.dto.MyScoutFieldReportResponse;
import com.typenull.pingdom.verification.api.dto.ScoutFieldReportCreateRequest;
import com.typenull.pingdom.verification.api.dto.ScoutFieldReportPageResponse;
import com.typenull.pingdom.verification.api.dto.ScoutFieldReportResponse;
import com.typenull.pingdom.verification.api.dto.ScoutFieldReportReviewRequest;
import com.typenull.pingdom.verification.domain.ScoutFieldReport;
import com.typenull.pingdom.verification.domain.ScoutFieldReportStatus;
import com.typenull.pingdom.verification.domain.exception.VisitorVerificationErrorCode;
import com.typenull.pingdom.verification.domain.exception.VisitorVerificationException;
import com.typenull.pingdom.verification.infrastructure.ScoutFieldReportRepository;
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
public class ScoutFieldReportService {

    private final ScoutFieldReportRepository reportRepository;
    private final UserRepository userRepository;
    private final MapPlaceRepository placeRepository;
    private final Clock clock;
    private final AdminAuditLogService adminAuditLogService;
    private final ScoutFieldReportMetrics metrics;
    private final ScoutEligibilityPolicy scoutEligibilityPolicy;

    @Transactional
    public MyScoutFieldReportResponse submit(Long userId, ScoutFieldReportCreateRequest request) {
        requireScout(userId);
        if (!placeRepository.existsById(request.placeId())) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.PLACE_NOT_FOUND);
        }
        if (reportRepository.existsByScoutUserIdAndPlaceIdAndReportTypeAndStatus(
                userId,
                request.placeId(),
                request.reportType(),
                ScoutFieldReportStatus.SUBMITTED
        )) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.SCOUT_FIELD_REPORT_ALREADY_SUBMITTED);
        }

        ScoutFieldReport report;
        try {
            report = ScoutFieldReport.submit(
                    userId,
                    request.placeId(),
                    request.reportType(),
                    request.description(),
                    request.evidenceUrl(),
                    LocalDateTime.now(clock)
            );
        } catch (IllegalArgumentException exception) {
            throw new VisitorVerificationException(
                    VisitorVerificationErrorCode.INVALID_SCOUT_FIELD_REPORT_DETAILS
            );
        }

        try {
            ScoutFieldReport saved = reportRepository.saveAndFlush(report);
            afterCommit(() -> metrics.recordReportSubmitted(saved.getReportType()));
            return MyScoutFieldReportResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_scout_field_report_active")) {
                throw new VisitorVerificationException(
                        VisitorVerificationErrorCode.SCOUT_FIELD_REPORT_ALREADY_SUBMITTED
                );
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public MyScoutFieldReportResponse getMine(Long userId, Long reportId) {
        requireScout(userId);
        ScoutFieldReport report = find(reportId);
        if (!report.getScoutUserId().equals(userId)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.SCOUT_FIELD_REPORT_FORBIDDEN);
        }
        return MyScoutFieldReportResponse.from(report);
    }

    @Transactional(readOnly = true)
    public MyScoutFieldReportPageResponse listMine(Long userId, int page, int limit) {
        requireScout(userId);
        Page<ScoutFieldReport> reports = reportRepository.findAllByScoutUserId(userId, pageRequest(page, limit));
        return new MyScoutFieldReportPageResponse(
                reports.getContent().stream().map(MyScoutFieldReportResponse::from).toList(),
                page,
                limit,
                reports.getTotalElements(),
                reports.getTotalPages(),
                reports.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public ScoutFieldReportPageResponse listForAdmin(
            Long adminUserId,
            ScoutFieldReportStatus status,
            int page,
            int limit
    ) {
        requireAdmin(adminUserId);
        Page<ScoutFieldReport> reports = status == null
                ? reportRepository.findAll(pageRequest(page, limit))
                : reportRepository.findAllByStatus(status, pageRequest(page, limit));
        return page(reports, page, limit);
    }

    @Transactional
    public ScoutFieldReportResponse review(
            Long adminUserId,
            Long reportId,
            ScoutFieldReportReviewRequest request
    ) {
        requireAdmin(adminUserId);
        ScoutFieldReport report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new VisitorVerificationException(
                        VisitorVerificationErrorCode.SCOUT_FIELD_REPORT_NOT_FOUND
                ));

        ScoutFieldReportStatus fromStatus = report.getStatus();
        Long beforeReviewerAdminUserId = report.getReviewerAdminUserId();
        String beforeReviewNote = report.getReviewNote();
        LocalDateTime beforeReviewedAt = report.getReviewedAt();
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            report.review(adminUserId, request.decision(), request.reviewNote(), now);
        } catch (IllegalStateException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.INVALID_SCOUT_FIELD_REPORT_STATE);
        } catch (IllegalArgumentException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.INVALID_SCOUT_FIELD_REPORT_REVIEW);
        }

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.SCOUT_FIELD_REPORT_REVIEWED,
                AdminAuditTargetType.SCOUT_FIELD_REPORT,
                report.getId(),
                report.getReviewNote(),
                reportState(report, fromStatus, beforeReviewerAdminUserId, beforeReviewNote, beforeReviewedAt),
                reportState(report, report.getStatus())
        );
        afterCommit(() -> metrics.recordReportStatusUpdate(fromStatus, report.getStatus()));
        return ScoutFieldReportResponse.from(report);
    }

    private void requireScout(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null
                || user.getRole() != UserRole.USER
                || user.isWithdrawn()
                || user.isCurrentlyBanned(now)
                || !scoutEligibilityPolicy.isEligible(userId)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.SCOUT_ACCOUNT_REQUIRED);
        }
    }

    private void requireAdmin(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null || user.getRole() != UserRole.ADMIN || user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.ADMIN_ACCOUNT_REQUIRED);
        }
    }

    private ScoutFieldReport find(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new VisitorVerificationException(
                        VisitorVerificationErrorCode.SCOUT_FIELD_REPORT_NOT_FOUND
                ));
    }

    private PageRequest pageRequest(int page, int limit) {
        return PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private ScoutFieldReportPageResponse page(Page<ScoutFieldReport> reports, int page, int limit) {
        return new ScoutFieldReportPageResponse(
                reports.getContent().stream().map(ScoutFieldReportResponse::from).toList(),
                page,
                limit,
                reports.getTotalElements(),
                reports.getTotalPages(),
                reports.hasNext()
        );
    }

    private Map<String, Object> reportState(ScoutFieldReport report, ScoutFieldReportStatus status) {
        return reportState(
                report,
                status,
                report.getReviewerAdminUserId(),
                report.getReviewNote(),
                report.getReviewedAt()
        );
    }

    private Map<String, Object> reportState(
            ScoutFieldReport report,
            ScoutFieldReportStatus status,
            Long reviewerAdminUserId,
            String reviewNote,
            LocalDateTime reviewedAt
    ) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("reportId", report.getId());
        state.put("scoutUserId", report.getScoutUserId());
        state.put("placeId", report.getPlaceId());
        state.put("reportType", report.getReportType());
        state.put("status", status);
        state.put("reviewerAdminUserId", reviewerAdminUserId);
        state.put("reviewNote", reviewNote);
        state.put("reviewedAt", reviewedAt);
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
