package com.typenull.pingdom.verification.application;

import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
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

/**
 * 현재 활동 가능한 Scout의 현장 제보 접수·본인 조회 및 관리자 심사를 조율.
 * 중복 제보는 사전 조회와 DB 제약으로 제한하며 심사는 잠금 조회 후 감사 이력을 남김.
 */
@Service
@RequiredArgsConstructor
public class ScoutFieldReportService {

    private final ScoutFieldReportRepository reportRepository;
    private final UserRepository userRepository;
    private final MapPlaceRepository placeRepository;
    private final AdminRoleAuthorizationService adminRoleAuthorizationService;
    private final Clock clock;
    private final AdminAuditLogService adminAuditLogService;
    private final ScoutFieldReportMetrics metrics;
    private final ScoutEligibilityPolicy scoutEligibilityPolicy;

    /**
     * 활동 자격과 장소 존재를 확인하고 같은 Scout·장소·유형의 미심사 중복을 거부.
     * flush의 지정 유일 제약도 동일 오류로 변환하며 제출 메트릭은 트랜잭션 커밋 후 기록.
     */
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

    /** 현재 Scout 자격을 확인한 뒤 본인 제보만 반환. 다른 소유자의 제보는 접근 금지 오류로 처리. */
    @Transactional(readOnly = true)
    public MyScoutFieldReportResponse getMine(Long userId, Long reportId) {
        requireScout(userId);
        ScoutFieldReport report = find(reportId);
        if (!report.getScoutUserId().equals(userId)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.SCOUT_FIELD_REPORT_FORBIDDEN);
        }
        return MyScoutFieldReportResponse.from(report);
    }

    /** 현재 자격이 있는 Scout의 제보를 생성 시각·ID 역순으로 조회. */
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

    /** SCOUT_REVIEW 권한으로 제보를 조회. 상태 null은 전체이며 페이지는 1부터 시작. */
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

    /**
     * SCOUT_REVIEW 권한 확인 후 제보를 쓰기 잠금으로 조회해 중복 심사를 방지.
     * 상태·입력 오류를 구분하고 전후 심사 정보를 감사 이력에 남기며 메트릭은 커밋 후 기록.
     */
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

    /** 미탈퇴·미정지 USER 계정이며 활성 프로필과 현재 활동 자격을 모두 갖춰야 함. */
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

    /** 단순 ADMIN 역할 여부 대신 SCOUT_REVIEW 상세 권한을 요구. */
    private void requireAdmin(Long userId) {
        adminRoleAuthorizationService.requirePermission(userId, AdminPermission.SCOUT_REVIEW);
    }

    /** 제보가 없으면 현장 제보 부재 오류를 발생시킴. */
    private ScoutFieldReport find(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new VisitorVerificationException(
                        VisitorVerificationErrorCode.SCOUT_FIELD_REPORT_NOT_FOUND
                ));
    }

    /** 1부터 시작하는 페이지 번호를 내부 offset으로 바꾸고 생성 시각·ID 역순을 적용. */
    private PageRequest pageRequest(int page, int limit) {
        return PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    /** 관리자용 응답 항목과 페이지 총계·다음 페이지 여부를 묶음. */
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

    /** 현재 제보의 심사 정보로 감사 이력 스냅샷을 생성. */
    private Map<String, Object> reportState(ScoutFieldReport report, ScoutFieldReportStatus status) {
        return reportState(
                report,
                status,
                report.getReviewerAdminUserId(),
                report.getReviewNote(),
                report.getReviewedAt()
        );
    }

    /** 공통 제보 식별 정보에 전달받은 상태·심사자·메모·시각을 결합해 변경 전후 이력을 구성. */
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

    /** 원인 예외 체인에서 지정 유일 제약만 식별해 다른 무결성 오류는 그대로 전파. */
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

    /**
     * 트랜잭션 동기화가 있으면 커밋 후 실행하고 없으면 즉시 실행.
     * 콜백 실패 재시도와 DB 커밋 롤백은 처리 범위 외.
     */
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
