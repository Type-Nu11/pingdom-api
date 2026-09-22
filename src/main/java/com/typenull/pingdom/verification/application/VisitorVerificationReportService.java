package com.typenull.pingdom.verification.application;

import com.typenull.pingdom.identity.domain.*;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.observability.VisitorVerificationReportMetrics;
import com.typenull.pingdom.verification.api.dto.*;
import com.typenull.pingdom.verification.domain.*;
import com.typenull.pingdom.verification.domain.exception.*;
import com.typenull.pingdom.verification.infrastructure.VisitorVerificationReportRepository;
import java.time.*;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 관광객 제보의 제출·본인 조회·관리자 심사와 감사 이력을 조율.
 * 중복은 사전 조회와 DB 제약으로 방어하고 심사는 잠금 조회로 직렬화.
 */
@Service
@RequiredArgsConstructor
public class VisitorVerificationReportService {
    private final VisitorVerificationReportRepository reportRepository;
    private final UserRepository userRepository;
    private final MapPlaceRepository placeRepository;
    private final Clock clock;
    private final AdminAuditLogService adminAuditLogService;
    private final VisitorVerificationReportMetrics metrics;

    /**
     * 관광객 계정·장소 존재·미심사 중복과 제보 내용을 검증한 뒤 저장.
     * 체크인 기록과 증빙 파일 존재는 이 흐름의 조회 대상에서 제외. 제출 메트릭은 커밋 후 기록.
     */
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
        VisitorVerificationReport report;
        try {
            report = VisitorVerificationReport.submit(userId, request.placeId(), request.reportType(),
                    request.description(), request.evidenceUrl(), request.waitTimeMinutes(),
                    request.languageCode(), request.couponUsageStatus(), request.crowdLevel(),
                    LocalDateTime.now(clock));
        } catch (IllegalArgumentException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.INVALID_REPORT_DETAILS);
        }

        VisitorVerificationReport saved;
        try {
            saved = reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_visitor_verification_report_active")) {
                throw new VisitorVerificationException(VisitorVerificationErrorCode.ACTIVE_REPORT_ALREADY_EXISTS);
            }
            throw exception;
        }
        afterCommit(() -> metrics.recordReportSubmitted(saved.getReportType()));
        return MyVisitorVerificationReportResponse.from(saved);
    }

    /** 관광객 계정의 본인 제보만 조회하며 타인 제보는 접근 금지로 거부. */
    @Transactional(readOnly = true)
    public MyVisitorVerificationReportResponse getMine(Long userId, Long reportId) {
        requireTourist(userId);
        VisitorVerificationReport report = find(reportId);
        if (!report.getReporterUserId().equals(userId)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.REPORT_FORBIDDEN);
        }
        return MyVisitorVerificationReportResponse.from(report);
    }

    /** 관광객 본인의 제보를 생성 시각·ID 역순으로 페이지 조회. */
    @Transactional(readOnly = true)
    public MyVisitorVerificationReportPageResponse listMine(Long userId, int page, int limit) {
        requireTourist(userId);
        Page<VisitorVerificationReport> reports =
                reportRepository.findAllByReporterUserId(userId, pageRequest(page, limit));
        return new MyVisitorVerificationReportPageResponse(
                reports.getContent().stream().map(MyVisitorVerificationReportResponse::from).toList(), page, limit,
                reports.getTotalElements(), reports.getTotalPages(), reports.hasNext());
    }

    /** 활성 관리자 계정으로 상태별 제보를 조회. 상태 null은 전체를 의미. */
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

    /**
     * 관리자 계정을 확인하고 제보를 쓰기 잠금 조회해 심사.
     * 변경 전 심사 정보를 미리 보관해 감사 이력을 만들고 상태 메트릭은 커밋 후 남김.
     */
    @Transactional
    public VisitorVerificationReportResponse review(Long adminUserId, Long reportId,
            VisitorVerificationReportReviewRequest request) {
        requireAdmin(adminUserId);
        VisitorVerificationReport report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new VisitorVerificationException(VisitorVerificationErrorCode.REPORT_NOT_FOUND));
        VisitorVerificationReportStatus fromStatus = report.getStatus();
        Long beforeReviewerAdminUserId = report.getReviewerAdminUserId();
        String beforeReviewNote = report.getReviewNote();
        LocalDateTime beforeReviewedAt = report.getReviewedAt();
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            report.review(adminUserId, request.decision(), request.reviewNote(), now);
        } catch (IllegalStateException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.INVALID_REPORT_STATE);
        } catch (IllegalArgumentException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.INVALID_REVIEW);
        }
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.VISITOR_VERIFICATION_REPORT_REVIEWED,
                AdminAuditTargetType.VISITOR_VERIFICATION_REPORT,
                report.getId(),
                report.getReviewNote(),
                reportState(report, fromStatus, beforeReviewerAdminUserId, beforeReviewNote, beforeReviewedAt),
                reportState(report, report.getStatus())
        );
        afterCommit(() -> metrics.recordReportStatusUpdate(fromStatus, report.getStatus()));
        return VisitorVerificationReportResponse.from(report);
    }

    /** 미탈퇴·현재 미정지 USER 계정만 허용. */
    private void requireTourist(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null || user.getRole() != UserRole.USER || user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.TOURIST_ACCOUNT_REQUIRED);
        }
    }

    /** ADMIN 역할과 탈퇴·정지 여부를 확인. 이 메서드에는 세부 권한 검사가 없음. */
    private void requireAdmin(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null || user.getRole() != UserRole.ADMIN || user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.ADMIN_ACCOUNT_REQUIRED);
        }
    }

    /** 제보를 찾지 못하면 REPORT_NOT_FOUND로 변환. */
    private VisitorVerificationReport find(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new VisitorVerificationException(VisitorVerificationErrorCode.REPORT_NOT_FOUND));
    }

    /** 외부 1-based 페이지를 내부 번호로 바꾸고 생성 시각·ID 역순 정렬을 적용. */
    private PageRequest pageRequest(int page, int limit) {
        return PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    /** 관리자용 상세 목록에 전체 건수·페이지 수·다음 페이지 여부를 결합. */
    private VisitorVerificationReportPageResponse page(Page<VisitorVerificationReport> reports, int page, int limit) {
        return new VisitorVerificationReportPageResponse(
                reports.getContent().stream().map(VisitorVerificationReportResponse::from).toList(), page, limit,
                reports.getTotalElements(), reports.getTotalPages(), reports.hasNext());
    }

    /** 원인 체인에서 지정한 DB 제약만 식별해 알려진 중복 오류로 변환. */
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

    /** 현재 심사 정보와 지정 상태로 감사 스냅샷을 생성. */
    private Map<String, Object> reportState(
            VisitorVerificationReport report,
            VisitorVerificationReportStatus status
    ) {
        return reportState(
                report,
                status,
                report.getReviewerAdminUserId(),
                report.getReviewNote(),
                report.getReviewedAt()
        );
    }

    /** 제보 ID와 전달된 심사 정보를 복사해 변경 전·후 감사 이력을 구성. */
    private Map<String, Object> reportState(
            VisitorVerificationReport report,
            VisitorVerificationReportStatus status,
            Long reviewerAdminUserId,
            String reviewNote,
            LocalDateTime reviewedAt
    ) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("reportId", report.getId());
        state.put("status", status);
        state.put("reviewerAdminUserId", reviewerAdminUserId);
        state.put("reviewNote", reviewNote);
        state.put("reviewedAt", reviewedAt);
        return state;
    }

    /** 동기화가 있으면 커밋 후 실행하고 없으면 즉시 실행. 실패 재시도 기능은 제공 범위 외. */
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
