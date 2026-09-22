package com.typenull.pingdom.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.admin.AdminRole;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignment;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignmentStatus;
import com.typenull.pingdom.identity.domain.repository.AdminRoleAssignmentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 관리자 역할 배정 API의 인증·세부 권한·대상 검색 및 배정 이력 보존을 검증.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class AdminRoleAssignmentSecurityIntegrationTest extends AuthRegressionIntegrationTestSupport {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AdminRoleAssignmentRepository assignmentRepository;

    /**
     * 역할 대상 검색·조회·배정·회수 경로 모두 미인증 요청을 401로 차단하는지 확인.
     */
    @Test
    void rolesRequireToken() throws Exception {
        mockMvc.perform(get("/admin/users/role-targets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mockMvc.perform(get("/admin/users/20/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mockMvc.perform(post("/admin/users/20/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ANALYST\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mockMvc.perform(delete("/admin/users/20/roles/ANALYST"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /**
     * 일반 사용자는 역할 대상 검색과 역할 조회에서 ACCESS_DENIED를 받는지 확인.
     */
    @Test
    void rolesRejectUser() throws Exception {
        User user = saveUser("role-normal-user", UserRole.USER);

        mockMvc.perform(get("/admin/users/role-targets")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/admin/users/20/roles")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /**
     * SUPER_ADMIN의 배정·조회·회수 흐름에서 중복 배정을 거절하고 회수한 배정 행을 이력으로 보존하는지 확인.
     */
    @Test
    void roleAssignmentLifecycle() throws Exception {
        User actor = saveUser("role-super-admin", UserRole.ADMIN);
        User target = saveUser("role-target-admin", UserRole.ADMIN);
        assignmentRepository.saveAndFlush(AdminRoleAssignment.assign(
                actor.getId(), AdminRole.SUPER_ADMIN, actor.getId(), LocalDateTime.now()
        ));
        String token = bearerToken(actor);

        mockMvc.perform(post("/admin/users/{userId}/roles", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CONTENT_MODERATOR\",\"reason\":\"장소 검수 배정\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminUserId").value(target.getId()))
                .andExpect(jsonPath("$.role").value("CONTENT_MODERATOR"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.permissions").isArray());

        mockMvc.perform(get("/admin/users/{userId}/roles", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("CONTENT_MODERATOR"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        mockMvc.perform(post("/admin/users/{userId}/roles", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CONTENT_MODERATOR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_ROLE_ASSIGNMENT_CONFLICT"));

        mockMvc.perform(delete("/admin/users/{userId}/roles/{role}", target.getId(), "CONTENT_MODERATOR")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"담당 업무 변경\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        MvcResult listResult = mockMvc.perform(get("/admin/users/{userId}/roles", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("REVOKED"))
                .andReturn();
        JsonNode roles = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(roles).hasSize(1);
        assertThat(assignmentRepository.findAllByAdminUserIdAndStatus(
                target.getId(), AdminRoleAssignmentStatus.REVOKED
        )).hasSize(1);
    }

    /**
     * ANALYST는 역할 대상 검색과 SUPPORT_OPERATOR 배정 권한이 없는지 확인.
     */
    @Test
    void analystCannotManageRoles() throws Exception {
        User actor = saveUser("role-analyst", UserRole.ADMIN);
        User target = saveUser("role-analyst-target", UserRole.ADMIN);
        assignmentRepository.saveAndFlush(AdminRoleAssignment.assign(
                actor.getId(), AdminRole.ANALYST, actor.getId(), LocalDateTime.now()
        ));

        mockMvc.perform(get("/admin/users/role-targets")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(actor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_PERMISSION_REQUIRED"));
        mockMvc.perform(post("/admin/users/{userId}/roles", target.getId())
                .header(HttpHeaders.AUTHORIZATION, bearerToken(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SUPPORT_OPERATOR\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_PERMISSION_REQUIRED"));
    }

    /**
     * 제재된 관리자도 대상 검색에 포함하되 일반 사용자·이메일은 제외하고 페이지 경계 및 page/limit 보정을 확인.
     */
    @Test
    void roleTargetPagination() throws Exception {
        User actor = saveUser("role-search-super-admin", UserRole.ADMIN);
        assignmentRepository.saveAndFlush(AdminRoleAssignment.assign(
                actor.getId(), AdminRole.SUPER_ADMIN, actor.getId(), LocalDateTime.now()
        ));
        User alpha = saveUser("roleSearchAlpha", UserRole.ADMIN);
        User banned = saveUser("roleSearchBanned", UserRole.ADMIN);
        banned.ban("검색 대상 포함 확인", LocalDateTime.now());
        userRepository.saveAndFlush(banned);
        User beta = saveUser("roleSearchBeta", UserRole.ADMIN);
        saveUser("roleSearchNormalUser", UserRole.USER);

        mockMvc.perform(get("/admin/users/role-targets")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(actor))
                        .param("keyword", "roleSearch")
                        .param("page", "1")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(2))
                .andExpect(jsonPath("$.users[0].userId").value(alpha.getId()))
                .andExpect(jsonPath("$.users[0].username").value("roleSearchAlpha"))
                .andExpect(jsonPath("$.users[1].userId").value(banned.getId()))
                .andExpect(jsonPath("$.users[0].email").doesNotExist())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));

        mockMvc.perform(get("/admin/users/role-targets")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(actor))
                        .param("keyword", "roleSearch")
                        .param("page", "2")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(1))
                .andExpect(jsonPath("$.users[0].userId").value(beta.getId()))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.hasNext").value(false));

        mockMvc.perform(get("/admin/users/role-targets")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(actor))
                        .param("keyword", "not-found"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));

        mockMvc.perform(get("/admin/users/role-targets")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(actor))
                        .param("keyword", "   ")
                        .param("page", "0")
                        .param("limit", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(4))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(100))
                .andExpect(jsonPath("$.totalCount").value(4))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    /**
     * 일반 사용자에 대한 역할 배정과 존재하지 않는 대상 조회가 서로 다른 오류 코드로 구분되는지 확인.
     */
    @Test
    void invalidRoleTargets() throws Exception {
        User actor = saveUser("role-target-validator", UserRole.ADMIN);
        assignmentRepository.saveAndFlush(AdminRoleAssignment.assign(
                actor.getId(), AdminRole.SUPER_ADMIN, actor.getId(), LocalDateTime.now()
        ));
        User normalUser = saveUser("role-invalid-target", UserRole.USER);
        String token = bearerToken(actor);

        mockMvc.perform(post("/admin/users/{userId}/roles", normalUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ANALYST\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN_ROLE_ASSIGNMENT_INVALID"));

        mockMvc.perform(get("/admin/users/{userId}/roles", 99999999L)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADMIN_TARGET_USER_NOT_FOUND"));
    }

    /**
     * 요청자 또는 대상 사용자의 역할을 지정해 저장하고 flush함.
     */
    private User saveUser(String username, UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("encoded-password")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(role)
                .build());
    }

    /**
     * 저장된 사용자 정보로 접근 토큰을 직접 발급해 Bearer 헤더를 생성.
     */
    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name()
        );
    }
}
