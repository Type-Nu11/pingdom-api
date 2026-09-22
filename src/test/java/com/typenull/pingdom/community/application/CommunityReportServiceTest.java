package com.typenull.pingdom.community.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.community.api.dto.CommunityReportCreateRequest;
import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityReportReason;
import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostCommentRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityReportRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CommunityReportServiceTest {
    @Mock private CommunityPostRepository posts;
    @Mock private CommunityPostCommentRepository comments;
    @Mock private CommunityReportRepository reports;
    private CommunityReportService service;
    private final CommunityReportCreateRequest request = new CommunityReportCreateRequest(CommunityReportReason.SPAM, "설명");

    /**
     * 조회 가능한 게시글과 모의 신고 저장소를 연결해 저장 시 제약 위반의 오류 변환을 확인.
     */
    @BeforeEach
    void setup() {
        service = new CommunityReportService(posts, comments, reports, Clock.systemUTC());
        when(posts.findByIdAndHiddenFalse(1L))
                .thenReturn(Optional.of(CommunityPost.create("TRAVEL", "제목", "내용", 1L)));
    }

    /**
     * 신고 저장 중 게시글·댓글 신고자의 유일 제약 충돌이 발생하면 ALREADY_REPORTED 도메인 오류로 변환하는지 검증.
     */
    @ParameterizedTest
    @ValueSource(strings = {"uk_community_report_reporter_post", "uk_community_report_reporter_comment"})
    void mapsDuplicateReportConstraints(String constraint) {
        when(reports.saveAndFlush(any())).thenThrow(failure(constraint));
        assertThatThrownBy(() -> service.reportPost(1L, 2L, request))
                .isInstanceOf(CommunityException.class).extracting("errorCode").isEqualTo(CommunityErrorCode.ALREADY_REPORTED);
    }

    /**
     * 외래 키·체크 제약 또는 제약명이 없는 무결성 오류는 중복 신고로 바꾸지 않고 원래 예외 그대로 전달하는지 검증.
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"fk_community_report_post", "ck_community_report_target"})
    void preservesOtherReportDatabaseFailures(String constraint) {
        DataIntegrityViolationException failure = failure(constraint);
        when(reports.saveAndFlush(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.reportPost(1L, 2L, request)).isSameAs(failure);
    }

    /**
     * 지정 제약명을 가진 Hibernate 예외를 Spring 무결성 예외로 감싸 실제 오류 원인 탐색 형태를 재현.
     */
    private DataIntegrityViolationException failure(String constraint) {
        return new DataIntegrityViolationException("constraint", new ConstraintViolationException("constraint", new SQLException(), constraint));
    }
}
