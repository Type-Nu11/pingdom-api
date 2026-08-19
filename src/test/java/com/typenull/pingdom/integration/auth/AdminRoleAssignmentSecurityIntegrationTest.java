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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AdminRoleAssignmentSecurityIntegrationTest extends AuthRegressionIntegrationTestSupport {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AdminRoleAssignmentRepository assignmentRepository;

    @Test
    void roleEndpointsRejectUnauthenticatedRequests() throws Exception {
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

    @Test
    void nonAdminCannotAccessRoleEndpoints() throws Exception {
        User user = saveUser("role-normal-user", UserRole.USER);

        mockMvc.perform(get("/admin/users/20/roles")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void superAdminCanAssignListAndRevokeRoleWithHistory() throws Exception {
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

    @Test
    void specializedAdminCannotManageRoles() throws Exception {
        User actor = saveUser("role-analyst", UserRole.ADMIN);
        User target = saveUser("role-analyst-target", UserRole.ADMIN);
        assignmentRepository.saveAndFlush(AdminRoleAssignment.assign(
                actor.getId(), AdminRole.ANALYST, actor.getId(), LocalDateTime.now()
        ));

        mockMvc.perform(post("/admin/users/{userId}/roles", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SUPPORT_OPERATOR\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_PERMISSION_REQUIRED"));
    }

    @Test
    void rejectsInvalidRoleTargetsWithIdentifiableErrors() throws Exception {
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

    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name()
        );
    }
}
