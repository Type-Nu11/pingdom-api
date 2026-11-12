package com.typenull.pingdom.identity.api.user;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.identity.application.command.ChangeInfoService;
import com.typenull.pingdom.identity.application.command.ProfileImageFileValidator;
import com.typenull.pingdom.identity.application.query.MyPageService;
import com.typenull.pingdom.identity.application.query.UserDataExportService;
import com.typenull.pingdom.identity.application.service.travel.TravelPurposePreferenceService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.domain.travel.TravelSchedule;
import com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandler;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;

class UsernameChangeControllerTest {

    private UserRepository users;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        when(users.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).username("current-user").build()));
        ChangeInfoService service = new ChangeInfoService(users, mock(PasswordEncoder.class),
                mock(ProfileImageFileValidator.class), mock(S3ObjectStorage.class));
        UsersController controller = new UsersController(mock(MyPageService.class), mock(UserDataExportService.class),
                service, mock(TravelPurposePreferenceService.class));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticatedUserResolver())
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class)))
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void allowsKeepingCurrentUsername() throws Exception {
        mvc.perform(post("/users/change-id").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newUsername\":\"current-user\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("아이디 변경 완료"));
    }

    @Test
    void preservesProfileDuplicateCodeForPrecheckedConflict() throws Exception {
        when(users.existsByUsername("taken-user")).thenReturn(true);

        mvc.perform(post("/users/change-id").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newUsername\":\"taken-user\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
    }

    @Test
    void preservesSameProfileDuplicateCodeForDatabaseRace() throws Exception {
        doThrow(integrityFailure("uk_users_username")).when(users).flush();

        mvc.perform(post("/users/change-id").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newUsername\":\"raced-user\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
    }

    @Test
    void preservesUnrelatedDatabaseFailureContract() throws Exception {
        doThrow(integrityFailure("uk_users_email")).when(users).flush();

        mvc.perform(post("/users/change-id").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newUsername\":\"next-user\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"));
    }

    @Test
    void preservesUserVersionConflictContract() throws Exception {
        doThrow(new ObjectOptimisticLockingFailureException(User.class, 1L)).when(users).flush();

        mvc.perform(post("/users/change-id").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newUsername\":\"next-user\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_CONCURRENT_MODIFICATION"));
    }

    @Test
    void doesNotClassifyAnotherEntityConflictAsUsernameConflict() throws Exception {
        doThrow(new ObjectOptimisticLockingFailureException(TravelSchedule.class, 1L)).when(users).flush();

        mvc.perform(post("/users/change-id").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newUsername\":\"next-user\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    private DataIntegrityViolationException integrityFailure(String constraint) {
        return new DataIntegrityViolationException(null, new ConstraintViolationException(
                "database failure", new SQLException("internal detail", "23505"), constraint));
    }

    private static class AuthenticatedUserResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == JwtAuthenticatedUser.class;
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                      NativeWebRequest request, WebDataBinderFactory binderFactory) {
            return new JwtAuthenticatedUser(1L, "current-user");
        }
    }
}
