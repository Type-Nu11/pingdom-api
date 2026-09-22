package com.typenull.pingdom.moderation.application.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.admin.AdminRole;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignment;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignmentStatus;
import com.typenull.pingdom.identity.domain.repository.AdminRoleAssignmentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.domain.NotificationType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminNotificationRecipientResolverTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-10T03:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AdminRoleAssignmentRepository assignmentRepository;
    @Mock
    private UserRepository userRepository;

    private AdminNotificationRecipientResolver resolver;

    /**
     * 역할 할당과 현재 계정 상태를 조합할 수신자 resolver를 고정 Clock으로 만든다.
     */
    @BeforeEach
    void setUp() {
        resolver = new AdminNotificationRecipientResolver(assignmentRepository, userRepository, CLOCK);
    }

    /**
     * 활성 역할 할당이 있는 관리자 중 정지된 최고 관리자는 제외하고 정상 콘텐츠 관리자의 ID만 수신자로 반환하는지 검증한다.
     */
    @Test
    void excludesBannedAdminRecipients() {
        AdminRoleAssignment contentModerator = AdminRoleAssignment.assign(
                1L,
                AdminRole.CONTENT_MODERATOR,
                null,
                LocalDateTime.now(CLOCK)
        );
        AdminRoleAssignment superAdmin = AdminRoleAssignment.assign(
                2L,
                AdminRole.SUPER_ADMIN,
                null,
                LocalDateTime.now(CLOCK)
        );
        when(assignmentRepository.findAllByRoleInAndStatus(
                anyCollection(),
                eq(AdminRoleAssignmentStatus.ACTIVE)
        )).thenReturn(List.of(contentModerator, superAdmin));

        User activeAdmin = user(1L, "activeAdmin", UserRole.ADMIN);
        User bannedAdmin = user(2L, "bannedAdmin", UserRole.ADMIN);
        bannedAdmin.ban("제재 중", LocalDateTime.now(CLOCK));
        when(userRepository.findAllById(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(activeAdmin, bannedAdmin));

        assertThat(resolver.resolve(NotificationType.ADMIN_REPORT_RECEIVED)).containsExactly(1L);
    }

    /**
     * 수신자 조회 결과를 준비하도록 지정 ID·이름·역할의 사용자 객체를 만든다.
     */
    private User user(Long id, String username, UserRole role) {
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("password")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(role)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
