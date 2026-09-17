package com.typenull.pingdom.moderation.application.service.community;

import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostComment;
import com.typenull.pingdom.community.domain.CommunityReport;
import com.typenull.pingdom.community.domain.CommunityReportStatus;
import com.typenull.pingdom.community.domain.CommunityReportTargetType;
import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostCommentRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityReportRepository;
import com.typenull.pingdom.moderation.api.dto.community.AdminCommunityReportActionResponse;
import com.typenull.pingdom.moderation.api.dto.community.AdminCommunityReportPageResponse;
import com.typenull.pingdom.moderation.api.dto.community.AdminCommunityReportResponse;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCommunityReportService {

    private final CommunityReportRepository reportRepository;
    private final CommunityPostRepository postRepository;
    private final CommunityPostCommentRepository commentRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminCommunityReportPageResponse list(
            CommunityReportStatus status,
            CommunityReportTargetType targetType,
            int page,
            int limit
    ) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        Page<CommunityReport> result = reportRepository.findAll(
                specification(status, targetType),
                PageRequest.of(safePage - 1, safeLimit, Sort.by(
                        Sort.Order.desc("createdAt"), Sort.Order.desc("id")
                ))
        );
        return new AdminCommunityReportPageResponse(
                result.getContent().stream().map(this::toItem).toList(),
                safePage,
                safeLimit,
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public AdminCommunityReportResponse get(Long reportId) {
        return toResponse(find(reportId));
    }

    @Transactional
    public AdminCommunityReportActionResponse accept(Long reportId, Long adminUserId) {
        CommunityReport report = findForUpdate(reportId);
        Map<String, Object> beforeState = state(report);
        LocalDateTime now = LocalDateTime.now(clock);
        boolean targetHidden = hideTarget(report, adminUserId, now);
        report.accept(adminUserId, now);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.COMMUNITY_REPORT_ACCEPTED,
                AdminAuditTargetType.COMMUNITY_REPORT,
                report.getId(),
                report.getReason().name(),
                beforeState,
                state(report)
        );
        return actionResponse(report, targetHidden);
    }

    @Transactional
    public AdminCommunityReportActionResponse decline(Long reportId, Long adminUserId) {
        CommunityReport report = findForUpdate(reportId);
        Map<String, Object> beforeState = state(report);
        LocalDateTime now = LocalDateTime.now(clock);
        report.decline(adminUserId, now);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.COMMUNITY_REPORT_DECLINED,
                AdminAuditTargetType.COMMUNITY_REPORT,
                report.getId(),
                report.getReason().name(),
                beforeState,
                state(report)
        );
        return actionResponse(report, isTargetHidden(report));
    }

    private Specification<CommunityReport> specification(
            CommunityReportStatus status,
            CommunityReportTargetType targetType
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (targetType == CommunityReportTargetType.POST) {
                predicates.add(builder.isNotNull(root.get("post")));
            } else if (targetType == CommunityReportTargetType.COMMENT) {
                predicates.add(builder.isNotNull(root.get("comment")));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private CommunityReport find(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.REPORT_NOT_FOUND));
    }

    private CommunityReport findForUpdate(Long reportId) {
        return reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.REPORT_NOT_FOUND));
    }

    // 신고 잠금 뒤 대상 잠금을 획득해 서로 다른 신고가 같은 콘텐츠를 동시에 처리해도 숨김 상태를 일관되게 유지한다.
    private boolean hideTarget(CommunityReport report, Long adminUserId, LocalDateTime now) {
        if (report.getTargetType() == CommunityReportTargetType.POST) {
            CommunityPost post = postRepository.findByIdForUpdate(report.getTargetId())
                    .orElseThrow(() -> new CommunityException(CommunityErrorCode.POST_NOT_FOUND));
            post.hide(adminUserId, now);
            return post.isHidden();
        }
        CommunityPostComment comment = commentRepository.findByIdForUpdate(report.getTargetId())
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.COMMENT_NOT_FOUND));
        comment.hide(adminUserId, now);
        return comment.isHidden();
    }

    private AdminCommunityReportPageResponse.Item toItem(CommunityReport report) {
        return new AdminCommunityReportPageResponse.Item(
                report.getId(), report.getTargetType(), report.getTargetId(), isTargetHidden(report),
                report.getReporterUserId(), report.getReason(), report.getStatus(),
                report.getCreatedAt(), report.getProcessedAt()
        );
    }

    private AdminCommunityReportResponse toResponse(CommunityReport report) {
        Long postId = report.getTargetType() == CommunityReportTargetType.POST
                ? report.getPost().getId()
                : report.getComment().getCommunityPost().getId();
        return new AdminCommunityReportResponse(
                report.getId(), report.getTargetType(), report.getTargetId(), postId, isTargetHidden(report),
                report.getReporterUserId(), report.getReason(), report.getDescription(), report.getStatus(),
                report.getCreatedAt(), report.getProcessedByAdminUserId(), report.getProcessedAt()
        );
    }

    private AdminCommunityReportActionResponse actionResponse(CommunityReport report, boolean targetHidden) {
        return new AdminCommunityReportActionResponse(
                report.getId(), report.getStatus(), report.getTargetType(), report.getTargetId(),
                targetHidden, report.getProcessedAt()
        );
    }

    private boolean isTargetHidden(CommunityReport report) {
        return report.getTargetType() == CommunityReportTargetType.POST
                ? report.getPost().isHidden()
                : report.getComment().isHidden();
    }

    private Map<String, Object> state(CommunityReport report) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", report.getStatus());
        state.put("targetType", report.getTargetType());
        state.put("targetId", report.getTargetId());
        state.put("targetHidden", isTargetHidden(report));
        state.put("processedByAdminUserId", report.getProcessedByAdminUserId());
        state.put("processedAt", report.getProcessedAt());
        return state;
    }
}
