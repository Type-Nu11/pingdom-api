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

/**
 * 커뮤니티 신고를 잠근 후 승인 시 대상 콘텐츠도 잠가 숨김 상태·처리 결과·감사 기록을 함께 저장합니다.
 * 이미 처리된 신고는 도메인 상태 검사로 거절하며, 사진 신고와 달리 사용자 정지나 신고자 점수 변경은 수행하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class AdminCommunityReportService {

    private final CommunityReportRepository reportRepository;
    private final CommunityPostRepository postRepository;
    private final CommunityPostCommentRepository commentRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;

    /**
     * 신고 처리 상태와 게시글/댓글 대상 유형이 주어지면 해당 조건을 적용해 최신순으로 반환합니다.
     * page는 1 이상·limit는 1~100으로 보정하고 각 결과에 현재 대상 숨김 상태를 포함합니다.
     */
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

    /**
     * 대상 숨김과 신고 승인을 같은 트랜잭션에 반영합니다. 신고 상태 검사가 실패하면 앞선 숨김도 롤백됩니다.
     */
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

    /**
     * 신고 행을 잠그고 미처리 상태의 신고만 거절해 처리 정보와 대상의 현재 숨김 상태를 반환합니다.
     * 신고가 없거나 이미 처리됐으면 거절하며 신고 전이와 감사 기록을 함께 저장하고 대상 콘텐츠의 숨김 상태는 바꾸지 않습니다.
     */
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
