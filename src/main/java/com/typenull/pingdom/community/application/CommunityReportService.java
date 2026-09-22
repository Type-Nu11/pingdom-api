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

/**
 * 공개 게시글 또는 해당 글의 공개 댓글에 대한 사용자별 신고를 접수.
 * 같은 대상 재신고는 사전 조회와 대상별 DB 유일 제약으로 차단하고, 다른 무결성 오류는 원인 보존을 위해 전파.
 */
@Service
@RequiredArgsConstructor
public class CommunityReportService {

    private static final Set<String> DUPLICATE_CONSTRAINTS = Set.of(
            "uk_community_report_reporter_post", "uk_community_report_reporter_comment");

    private final CommunityPostRepository postRepository;
    private final CommunityPostCommentRepository commentRepository;
    private final CommunityReportRepository reportRepository;
    private final Clock clock;

    // 인증 사용자를 신고자로 지정하고 동일 대상 재신고를 차단.
    @Transactional
    public CommunityReportCreateResponse reportPost(long postId, long reporterUserId, CommunityReportCreateRequest request) {
        CommunityPost post = postRepository.findByIdAndHiddenFalse(postId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.POST_NOT_FOUND));
        if (reportRepository.existsByReporterUserIdAndPost_Id(reporterUserId, postId)) {
            throw new CommunityException(CommunityErrorCode.ALREADY_REPORTED);
        }
        return save(CommunityReport.reportPost(reporterUserId, post, request.reason(), request.description(), LocalDateTime.now(clock)));
    }

    // 다른 게시글의 댓글 ID를 사용한 요청은 없는 댓글로 처리.
    /**
     * 공개 게시글에 속한 공개 댓글인지 확인해 사용자 신고를 저장·flush하고 신고 ID와 상태를 반환.
     * 다른 글의 댓글은 없는 대상으로 처리하고 기존 신고 또는 대상별 고유 제약 충돌은 ALREADY_REPORTED로 거절.
     */
    @Transactional
    public CommunityReportCreateResponse reportComment(long postId, long commentId, long reporterUserId,
                                                      CommunityReportCreateRequest request) {
        if (!postRepository.existsByIdAndHiddenFalse(postId)) {
            throw new CommunityException(CommunityErrorCode.POST_NOT_FOUND);
        }
        CommunityPostComment comment = commentRepository.findByIdAndCommunityPost_IdAndHiddenFalseAndCommunityPost_HiddenFalse(commentId, postId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.COMMENT_NOT_FOUND));
        if (reportRepository.existsByReporterUserIdAndComment_Id(reporterUserId, commentId)) {
            throw new CommunityException(CommunityErrorCode.ALREADY_REPORTED);
        }
        return save(CommunityReport.reportComment(reporterUserId, comment, request.reason(), request.description(), LocalDateTime.now(clock)));
    }

    // 동시 접수로 발생한 UNIQUE 충돌만 중복 신고 오류로 변환.
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
