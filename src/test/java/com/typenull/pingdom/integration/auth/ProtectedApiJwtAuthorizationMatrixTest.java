package com.typenull.pingdom.integration.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.shared.security.jwt.JwtProperties;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.stream.Stream;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
class ProtectedApiJwtAuthorizationMatrixTest extends AuthRegressionIntegrationTestSupport {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private Clock clock;

    @ParameterizedTest(name = "{0} rejects missing token")
    @MethodSource("protectedGetEndpoints")
    void protectedApiRejectsMissingToken(String endpoint) throws Exception {
        mockMvc.perform(get(endpoint))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @ParameterizedTest(name = "{0} rejects expired token")
    @MethodSource("protectedGetEndpoints")
    void protectedApiRejectsExpiredToken(String endpoint) throws Exception {
        User user = createUser("expiredUser" + endpointName(endpoint));
        String expiredToken = generateExpiredAccessToken(user);

        mockMvc.perform(get(endpoint)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("EXPIRED_TOKEN"));
    }

    @ParameterizedTest(name = "{0} rejects tampered token")
    @MethodSource("protectedGetEndpoints")
    void protectedApiRejectsTamperedToken(String endpoint) throws Exception {
        User user = createUser("tamperedUser" + endpointName(endpoint));
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());

        mockMvc.perform(get(endpoint)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamper(accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @ParameterizedTest(name = "{0} accepts valid token")
    @MethodSource("protectedGetEndpoints")
    void protectedApiAcceptsValidToken(String endpoint) throws Exception {
        User user = createUser("validUser" + endpointName(endpoint));
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());

        assertProtectedGetSucceeds(endpoint, accessToken);
    }

    @Test
    void withdrawnUserCannotAccessProtectedApiWithExistingToken() throws Exception {
        User user = createUser("withdrawnMatrixUser");
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());

        user.withdraw(
                "withdrawn_user_" + user.getId(),
                "withdrawn_user_%d@withdrawn.local".formatted(user.getId()),
                "encoded-withdrawn-password",
                LocalDateTime.now(clock)
        );
        userRepository.saveAndFlush(user);

        assertInvalidToken("/users/me", accessToken);
    }

    @Test
    void permanentBannedUserCannotAccessProtectedApiWithExistingToken() throws Exception {
        User user = createUser("permanentBanMatrixUser");
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());

        user.ban("영구 밴 테스트", LocalDateTime.now(clock));
        userRepository.saveAndFlush(user);

        assertInvalidToken("/users/me", accessToken);
    }

    @Test
    void activeTemporaryBannedUserCannotAccessProtectedApiWithExistingToken() throws Exception {
        User user = createUser("temporaryBanMatrixUser");
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        LocalDateTime now = LocalDateTime.now(clock);

        user.ban("임시 밴 테스트", now, now.plusDays(1));
        userRepository.saveAndFlush(user);

        assertInvalidToken("/users/me", accessToken);
    }

    @Test
    void expiredTemporaryBannedUserCanAccessProtectedApiWithExistingToken() throws Exception {
        User user = createUser("expiredTemporaryBanMatrixUser");
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        LocalDateTime now = LocalDateTime.now(clock);

        user.ban("만료된 임시 밴 테스트", now.minusDays(2), now.minusDays(1));
        userRepository.saveAndFlush(user);

        assertProtectedGetSucceeds("/users/me", accessToken);
    }

    @Test
    void errorDispatchIsNotRejectedAsInvalidToken() throws Exception {
        mockMvc.perform(get("/error")
                        .with(request -> {
                            request.setDispatcherType(DispatcherType.ERROR);
                            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/places");
                            return request;
                        }))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    private static Stream<String> protectedGetEndpoints() {
        return Stream.of("/places", "/places/information-reports", "/map/posts", "/users/me");
    }

    private void assertProtectedGetSucceeds(String endpoint, String accessToken) throws Exception {
        mockMvc.perform(get(endpoint)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private void assertInvalidToken(String endpoint, String accessToken) throws Exception {
        mockMvc.perform(get(endpoint)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    private String generateExpiredAccessToken(User user) {
        Instant issuedAt = Instant.now().minusSeconds(120);
        Instant expiredAt = Instant.now().minusSeconds(60);
        SecretKey secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("type", "access")
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiredAt))
                .signWith(secretKey)
                .compact();
    }

    private String tamper(String token) {
        String[] parts = token.split("\\.", 3);
        char firstPayloadChar = parts[1].charAt(0);
        char replacement = firstPayloadChar == 'a' ? 'b' : 'a';
        return parts[0] + "." + replacement + parts[1].substring(1) + "." + parts[2];
    }

    private String endpointName(String endpoint) {
        return endpoint.replace("/", "").replace("-", "");
    }
}
