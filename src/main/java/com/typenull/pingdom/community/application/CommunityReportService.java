package com.typenull.pingdom.community.application;

import com.typenull.pingdom.community.api.dto.CommunityReportCreateRequest;
import com.typenull.pingdom.community.api.dto.CommunityReportCreateResponse;
import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostComment;
import com.typenull.pingdom.community.domain.CommunityReport;
import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostCommentRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityReportRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityReportService {

    private static final Set<String> DUPLICATE_CONSTRAINTS = Set.of(
            "uk_community_report_reporter_post", "uk_community_report_reporter_comment");

    private final CommunityPostRepository postRepository;
    private final CommunityPostCommentRepository commentRepository;
    private final CommunityReportRepository reportRepository;
    private final Clock clock;

    // 인증 사용자를 신고자로 지정하고 동일 대상 재신고를 차단한다.
    @Transactional
    public CommunityReportCreateResponse reportPost(long postId, long reporterUserId, CommunityReportCreateRequest request) {
        CommunityPost post = postRepository.findById(postId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.POST_NOT_FOUND));
        if (reportRepository.existsByReporterUserIdAndPost_Id(reporterUserId, postId)) {
            throw new CommunityException(CommunityErrorCode.ALREADY_REPORTED);
        }
        return save(CommunityReport.reportPost(reporterUserId, post, request.reason(), request.description(), LocalDateTime.now(clock)));
    }

    // 다른 게시글의 댓글 ID를 사용한 요청은 없는 댓글로 처리한다.
    @Transactional
    public CommunityReportCreateResponse reportComment(long postId, long commentId, long reporterUserId,
                                                      CommunityReportCreateRequest request) {
        if (!postRepository.existsById(postId)) {
            throw new CommunityException(CommunityErrorCode.POST_NOT_FOUND);
        }
        CommunityPostComment comment = commentRepository.findByIdAndCommunityPost_Id(commentId, postId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.COMMENT_NOT_FOUND));
        if (reportRepository.existsByReporterUserIdAndComment_Id(reporterUserId, commentId)) {
            throw new CommunityException(CommunityErrorCode.ALREADY_REPORTED);
        }
        return save(CommunityReport.reportComment(reporterUserId, comment, request.reason(), request.description(), LocalDateTime.now(clock)));
    }

    // 동시 접수로 발생한 UNIQUE 충돌만 중복 신고 오류로 변환한다.
    private CommunityReportCreateResponse save(CommunityReport report) {
        try {
            CommunityReport saved = reportRepository.saveAndFlush(report);
            return new CommunityReportCreateResponse(saved.getId(), saved.getStatus());
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && violation.getConstraintName() != null
                        && DUPLICATE_CONSTRAINTS.contains(violation.getConstraintName())) {
                    throw new CommunityException(CommunityErrorCode.ALREADY_REPORTED);
                }
            }
            throw exception;
        }
    }
}
