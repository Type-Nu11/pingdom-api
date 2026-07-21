package com.typenull.pingdom.verification.application;

import com.typenull.pingdom.identity.domain.*;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.verification.api.dto.*;
import com.typenull.pingdom.verification.domain.*;
import com.typenull.pingdom.verification.domain.exception.*;
import com.typenull.pingdom.verification.infrastructure.VisitorVerificationReportRepository;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VisitorVerificationReportService {
    private final VisitorVerificationReportRepository reportRepository;
    private final UserRepository userRepository;
    private final MapPlaceRepository placeRepository;
    private final Clock clock;

    @Transactional
    public MyVisitorVerificationReportResponse submit(Long userId, VisitorVerificationReportCreateRequest request) {
        requireTourist(userId);
        if (!placeRepository.existsById(request.placeId())) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.PLACE_NOT_FOUND);
        }
        if (reportRepository.existsByReporterUserIdAndPlaceIdAndReportTypeAndStatus(userId, request.placeId(),
                request.reportType(), VisitorVerificationReportStatus.SUBMITTED)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.ACTIVE_REPORT_ALREADY_EXISTS);
        }
        try {
            return MyVisitorVerificationReportResponse.from(reportRepository.saveAndFlush(
                    VisitorVerificationReport.submit(userId, request.placeId(), request.reportType(),
                            request.description(), request.evidenceUrl(), request.waitTimeMinutes(),
                            request.languageCode(), request.couponUsageStatus(), request.crowdLevel(),
                            LocalDateTime.now(clock))));
        } catch (IllegalArgumentException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.INVALID_REPORT_DETAILS);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_visitor_verification_report_active")) {
                throw new VisitorVerificationException(VisitorVerificationErrorCode.ACTIVE_REPORT_ALREADY_EXISTS);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public MyVisitorVerificationReportResponse getMine(Long userId, Long reportId) {
        requireTourist(userId);
        VisitorVerificationReport report = find(reportId);
        if (!report.getReporterUserId().equals(userId)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.REPORT_FORBIDDEN);
        }
        return MyVisitorVerificationReportResponse.from(report);
    }

    @Transactional(readOnly = true)
    public MyVisitorVerificationReportPageResponse listMine(Long userId, int page, int limit) {
        requireTourist(userId);
        Page<VisitorVerificationReport> reports =
                reportRepository.findAllByReporterUserId(userId, pageRequest(page, limit));
        return new MyVisitorVerificationReportPageResponse(
                reports.getContent().stream().map(MyVisitorVerificationReportResponse::from).toList(), page, limit,
                reports.getTotalElements(), reports.getTotalPages(), reports.hasNext());
    }

    @Transactional(readOnly = true)
    public VisitorVerificationReportPageResponse listForAdmin(Long adminUserId,
            VisitorVerificationReportStatus status, int page, int limit) {
        requireAdmin(adminUserId);
        PageRequest pageable = pageRequest(page, limit);
        Page<VisitorVerificationReport> reports = status == null
                ? reportRepository.findAll(pageable)
                : reportRepository.findAllByStatus(status, pageable);
        return page(reports, page, limit);
    }

    @Transactional
    public VisitorVerificationReportResponse review(Long adminUserId, Long reportId,
            VisitorVerificationReportReviewRequest request) {
        requireAdmin(adminUserId);
        VisitorVerificationReport report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new VisitorVerificationException(VisitorVerificationErrorCode.REPORT_NOT_FOUND));
        try {
            report.review(adminUserId, request.decision(), request.reviewNote(), LocalDateTime.now(clock));
            return VisitorVerificationReportResponse.from(report);
        } catch (IllegalStateException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.INVALID_REPORT_STATE);
        } catch (IllegalArgumentException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.INVALID_REVIEW);
        }
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

    private VisitorVerificationReport find(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new VisitorVerificationException(VisitorVerificationErrorCode.REPORT_NOT_FOUND));
    }

    private PageRequest pageRequest(int page, int limit) {
        return PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private VisitorVerificationReportPageResponse page(Page<VisitorVerificationReport> reports, int page, int limit) {
        return new VisitorVerificationReportPageResponse(
                reports.getContent().stream().map(VisitorVerificationReportResponse::from).toList(), page, limit,
                reports.getTotalElements(), reports.getTotalPages(), reports.hasNext());
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
