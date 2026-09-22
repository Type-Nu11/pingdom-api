package com.typenull.pingdom.moderation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.application.service.ReportPolicyService;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.admin.AdminRole;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignment;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignmentStatus;
import com.typenull.pingdom.identity.domain.repository.AdminRoleAssignmentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.AdminPostService;
import com.typenull.pingdom.moderation.application.service.appeal.ReportAppealService;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.application.service.post.AdminPostServiceImpl;
import com.typenull.pingdom.moderation.application.service.report.AdminReportServiceImpl;
import com.typenull.pingdom.moderation.application.service.user.sanction.UserSanctionCommandService;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.infrastructure.persistence.ReportAppealRepository;
import com.typenull.pingdom.moderation.outbox.notification.AdminNotificationOutboxPublisher;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReportModerationPermissionTest {
    private static final Long ADMIN_ID = 10L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC);

    private final UserRepository users = mock(UserRepository.class);
    private final AdminRoleAssignmentRepository assignments = mock(AdminRoleAssignmentRepository.class);
    private final PostReportRepository reports = mock(PostReportRepository.class);
    private final MapImageRepository images = mock(MapImageRepository.class);
    private final ReportAppealRepository appeals = mock(ReportAppealRepository.class);
    private final AdminPostService posts = mock(AdminPostService.class);
    private final UserSanctionCommandService sanctions = mock(UserSanctionCommandService.class);
    private final AdminAuditLogService audit = mock(AdminAuditLogService.class);
    private final ReportPolicyService policy = mock(ReportPolicyService.class);
    private final AdminNotificationOutboxPublisher notifications = mock(AdminNotificationOutboxPublisher.class);
    private final PlaceGrowthService growth = mock(PlaceGrowthService.class);
    private final S3ObjectDeleteOutboxPublisher s3 = mock(S3ObjectDeleteOutboxPublisher.class);
    private final AdminRoleAuthorizationService authorization =
            new AdminRoleAuthorizationService(users, assignments, CLOCK);
    private final AdminReportServiceImpl reportService = new AdminReportServiceImpl(
            reports, images, users, posts, sanctions, audit, policy, notifications, authorization, CLOCK);
    private final AdminPostServiceImpl postService = new AdminPostServiceImpl(
            images, reports, growth, s3, audit, authorization, CLOCK);
    private final ReportAppealService appealService = new ReportAppealService(
            appeals, reports, images, users, posts, sanctions, audit, authorization, CLOCK);

    enum Actor {
        ANALYST, NO_ROLE, REVOKED, CONTENT, SUPPORT, CONTENT_AND_SUPPORT, SUPER_ADMIN
    }

    enum Operation {
        ACCEPT(true, AdminErrorCode.REPORT_NOT_FOUND),
        DECLINE(false, AdminErrorCode.REPORT_NOT_FOUND),
        ACCEPT_BULK(true, AdminErrorCode.POST_NOT_FOUND),
        DECLINE_BULK(false, AdminErrorCode.POST_NOT_FOUND),
        DELETE(false, AdminErrorCode.POST_NOT_FOUND),
        HIDE(false, AdminErrorCode.POST_NOT_FOUND),
        RESTORE(false, AdminErrorCode.POST_NOT_FOUND),
        APPROVE_APPEAL(true, AdminErrorCode.APPEAL_NOT_FOUND),
        REJECT_APPEAL(false, AdminErrorCode.APPEAL_NOT_FOUND);

        final boolean changesSanction;
        final AdminErrorCode missingTarget;

        /**
         * 작업의 제재 변경 여부와 권한 통과 후 기대할 대상 없음 오류를 연결.
         */
        Operation(boolean changesSanction, AdminErrorCode missingTarget) {
            this.changesSanction = changesSanction;
            this.missingTarget = missingTarget;
        }
    }

    /**
     * 모든 신고·게시글·이의 제기 작업과 역할 조합의 곱을 공급해 권한 누락 및 회수 상태까지 검사.
     */
    static Stream<Arguments> permissionCases() {
        return Stream.of(Operation.values()).flatMap(operation ->
                Stream.of(Actor.values()).map(actor -> Arguments.of(operation, actor)));
    }

    /**
     * 실제 역할 권한 서비스로 각 작업의 접근을 판정해 허용 시 대상 없음 오류까지 도달하고 거부 시 대상 저장소조차 호출하지 않는지 검증.
     * 제재를 바꾸는 작업의 복합 권한과 모든 조합에서 부수 효과가 없는 결과도 확인.
     */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("permissionCases")
    void checksPermissionsBeforeModerationAccess(Operation operation, Actor actor) {
        when(users.findById(ADMIN_ID)).thenReturn(Optional.of(User.builder()
                .id(ADMIN_ID).username("admin").role(UserRole.ADMIN).build()));
        List<AdminRoleAssignment> roles = switch (actor) {
            case ANALYST -> List.of(assignment(AdminRole.ANALYST));
            case NO_ROLE -> List.of();
            case REVOKED -> {
                AdminRoleAssignment revoked = assignment(AdminRole.SUPER_ADMIN);
                revoked.revoke(LocalDateTime.now(CLOCK));
                yield List.of(revoked);
            }
            case CONTENT -> List.of(assignment(AdminRole.CONTENT_MODERATOR));
            case SUPPORT -> List.of(assignment(AdminRole.SUPPORT_OPERATOR));
            case CONTENT_AND_SUPPORT -> List.of(assignment(AdminRole.CONTENT_MODERATOR),
                    assignment(AdminRole.SUPPORT_OPERATOR));
            case SUPER_ADMIN -> List.of(assignment(AdminRole.SUPER_ADMIN));
        };
        when(assignments.findAllByAdminUserIdAndStatus(ADMIN_ID, AdminRoleAssignmentStatus.ACTIVE))
                .thenReturn(roles.stream().filter(AdminRoleAssignment::isActive).toList());
        boolean allowed = actor == Actor.SUPER_ADMIN || actor == Actor.CONTENT_AND_SUPPORT
                || (actor == Actor.CONTENT && !operation.changesSanction);

        // 허용 시 대상 조회까지 도달하며, 거부 시에는 조회조차 하지 않아 상태 변경을 시작할 수 없음.
        assertThatThrownBy(() -> invoke(operation)).isInstanceOfSatisfying(AdminException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(
                        allowed ? operation.missingTarget : AdminErrorCode.ADMIN_PERMISSION_REQUIRED));
        verifyNoInteractions(posts, sanctions, audit, policy, notifications, growth, s3);
        if (!allowed) {
            verifyNoInteractions(reports, images, appeals);
        }
    }

    /**
     * 고정 시각에 활성 관리자 역할을 할당해 역할 조합별 실제 권한 판정에 사용.
     */
    private AdminRoleAssignment assignment(AdminRole role) {
        return AdminRoleAssignment.assign(ADMIN_ID, role, ADMIN_ID, LocalDateTime.now(CLOCK));
    }

    /**
     * 작업 enum을 신고 수락·거절, 게시글 삭제·숨김·복원, 이의 제기 승인·반려 서비스 호출에 연결.
     */
    private void invoke(Operation operation) {
        switch (operation) {
            case ACCEPT -> reportService.acceptReport(1L, ADMIN_ID);
            case DECLINE -> reportService.declineReport(1L, ADMIN_ID);
            case ACCEPT_BULK -> reportService.acceptPostReports(1L, ADMIN_ID);
            case DECLINE_BULK -> reportService.declinePostReports(1L, ADMIN_ID);
            case DELETE -> postService.deletePost(1L, ADMIN_ID);
            case HIDE -> postService.hidePost(1L, "reason", ADMIN_ID);
            case RESTORE -> postService.restorePost(1L, "reason", ADMIN_ID);
            case APPROVE_APPEAL -> appealService.approve(1L, "reason", ADMIN_ID);
            case REJECT_APPEAL -> appealService.reject(1L, "reason", ADMIN_ID);
        }
    }
}
