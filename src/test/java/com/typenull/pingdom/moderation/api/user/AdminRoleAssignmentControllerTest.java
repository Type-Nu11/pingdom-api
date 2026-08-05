package com.typenull.pingdom.moderation.api.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.identity.application.service.admin.AdminRoleAssignmentService;
import com.typenull.pingdom.identity.domain.admin.AdminRole;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignmentStatus;
import com.typenull.pingdom.moderation.api.dto.user.AdminRoleAssignmentResponse;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@ExtendWith(MockitoExtension.class)
class AdminRoleAssignmentControllerTest {

    @Mock private AdminRoleAssignmentService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminRoleAssignmentController(service))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                                  NativeWebRequest request, WebDataBinderFactory binderFactory) {
                        return new JwtAuthenticatedUser(10L, "admin");
                    }
                })
                .build();
    }

    @Test
    void exposesRoleListAssignAndRevokeContracts() throws Exception {
        AdminRoleAssignmentResponse response = new AdminRoleAssignmentResponse(
                1L, 20L, AdminRole.CONTENT_MODERATOR, AdminRoleAssignmentStatus.ACTIVE,
                10L, LocalDateTime.of(2026, 8, 5, 12, 0), null,
                List.of(com.typenull.pingdom.identity.domain.admin.AdminPermission.PLACE_MODERATE)
        );
        when(service.list(10L, 20L)).thenReturn(List.of(response));
        when(service.assign(any(), any(), any())).thenReturn(response);
        when(service.revoke(10L, 20L, AdminRole.CONTENT_MODERATOR, "업무 변경")).thenReturn(response);

        mockMvc.perform(get("/admin/users/20/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("CONTENT_MODERATOR"));
        mockMvc.perform(post("/admin/users/20/roles")
                        .contentType("application/json")
                        .content("{\"role\":\"CONTENT_MODERATOR\",\"reason\":\"업무 변경\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(delete("/admin/users/20/roles/CONTENT_MODERATOR")
                        .contentType("application/json")
                        .content("{\"reason\":\"업무 변경\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminUserId").value(20));
    }
}
