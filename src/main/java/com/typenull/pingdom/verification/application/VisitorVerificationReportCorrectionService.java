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

/**
 * 본인 방문 제보의 정정 제출과 관리자 심사를 조율한다.
 * 정정 승인 시 원본 내용을 바꾸고 재심사 상태로 돌리며 정정과 원본 변경을 같은 DB 트랜잭션에 둔다.
 */
@Service
@RequiredArgsConstructor
public class VisitorVerificationReportCorrectionService {

    private final VisitorVerificationReportRepository reportRepository;
    private final VisitorVerificationReportCorrectionRepository correctionRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final AdminAuditLogService adminAuditLogService;
    private final VisitorVerificationReportMetrics metrics;

    /**
     * 관광객 계정 확인 후 원본 제보를 잠가 소유권·정정 가능 상태·진행 중 중복을 검사한다.
     * 정정만 저장하고 원본은 유지하며 알려진 유일 제약 위반을 중복 정정 오류로 변환한다.
     */
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

    /** 원본 작성자만 자신의 정정 이력을 생성 시각·ID 역순으로 조회할 수 있다. */
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

    /** 활성 관리자 계정으로 정정 목록을 조회하며 상태 null은 전체 범위다. */
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

    /**
     * 정정 행을 잠근 뒤 원본 제보를 잠가 심사하고 승인된 정정만 원본에 반영한다.
     * 다른 미심사 제보가 있으면 승인 반영을 거부하며 flush 제약 오류도 같은 중복 오류로 처리한다.
     * 감사 이력을 남기고 커밋 후 정정 및 원본 상태 메트릭을 기록한다.
     */
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

    /** USER 역할의 미탈퇴·현재 미정지 계정만 허용한다. */
    private void requireTourist(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null || user.getRole() != UserRole.USER || user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.TOURIST_ACCOUNT_REQUIRED);
        }
    }

    /** ADMIN 역할과 계정 탈퇴·현재 정지를 확인한다. 상세 관리자 권한은 여기서 검사하지 않는다. */
    private void requireAdmin(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null || user.getRole() != UserRole.ADMIN || user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.ADMIN_ACCOUNT_REQUIRED);
        }
    }

    /** 원본 제보를 조회하고 없으면 REPORT_NOT_FOUND를 전달한다. */
    private VisitorVerificationReport findReport(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new VisitorVerificationException(VisitorVerificationErrorCode.REPORT_NOT_FOUND));
    }

    /** 원본 제보의 쓰기 잠금 조회를 통해 상태 확인과 변경 사이의 동시 갱신을 제한한다. */
    private VisitorVerificationReport findReportForUpdate(Long reportId) {
        return reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new VisitorVerificationException(VisitorVerificationErrorCode.REPORT_NOT_FOUND));
    }

    /** 1부터 시작하는 요청 페이지를 변환하고 생성 시각·ID 역순을 적용한다. */
    private PageRequest pageRequest(int page, int limit) {
        return PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    /** 원본 자신을 제외하고 같은 작성자·장소·유형의 SUBMITTED 제보가 있는지 확인한다. */
    private boolean hasOtherActiveReport(VisitorVerificationReport report) {
        return reportRepository.existsByReporterUserIdAndPlaceIdAndReportTypeAndStatusAndIdNot(
                report.getReporterUserId(),
                report.getPlaceId(),
                report.getReportType(),
                VisitorVerificationReportStatus.SUBMITTED,
                report.getId()
        );
    }

    /** 정정 심사 정보와 원본 제보 내용을 함께 복사해 전후 감사 기록을 만든다. */
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

    /** 원본의 ID·상태·본문·증빙·구조화 값을 감사 기록용 맵에 복사한다. */
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

    /** 원인 체인에서 지정 DB 제약을 찾아 알려진 중복 실패만 변환한다. */
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

    /** 트랜잭션 동기화가 활성화되면 커밋 뒤 실행하고, 없으면 바로 실행한다. */
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
