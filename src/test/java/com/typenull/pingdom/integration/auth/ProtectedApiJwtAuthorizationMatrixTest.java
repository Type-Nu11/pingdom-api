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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 보호 API에서 JWT 누락·만료·변조·유형 및 사용자 탈퇴·제재 상태를 구분해 검증.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class ProtectedApiJwtAuthorizationMatrixTest extends AuthRegressionIntegrationTestSupport {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private Clock clock;

    /**
     * 각 보호 GET 경로에서 토큰 누락을 JSON 401과 INVALID_TOKEN으로 반환하는지 확인.
     */
    @ParameterizedTest(name = "{0} rejects missing token")
    @MethodSource("protectedGetEndpoints")
    void missingToken(String endpoint) throws Exception {
        mockMvc.perform(get(endpoint))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /**
     * 서명은 유효하지만 만료된 접근 토큰에 EXPIRED_TOKEN을 반환하는지 확인.
     */
    @ParameterizedTest(name = "{0} rejects expired token")
    @MethodSource("protectedGetEndpoints")
    void expiredToken(String endpoint) throws Exception {
        User user = createUser("expiredUser" + endpointName(endpoint));
        String expiredToken = generateExpiredAccessToken(user);

        mockMvc.perform(get(endpoint)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("EXPIRED_TOKEN"));
    }

    /**
     * payload 한 글자를 변조한 접근 토큰은 INVALID_TOKEN으로 거절하는지 확인.
     */
    @ParameterizedTest(name = "{0} rejects tampered token")
    @MethodSource("protectedGetEndpoints")
    void tamperedToken(String endpoint) throws Exception {
        User user = createUser("tamperedUser" + endpointName(endpoint));
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());

        mockMvc.perform(get(endpoint)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamper(accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /**
     * 갱신 토큰을 Bearer 접근 토큰처럼 제출해도 사용자 정보 조회를 허용하지 않는지 확인.
     */
    @Test
    void refreshTokenAsBearer() throws Exception {
        String refreshToken = jwtTokenProvider.generateRefreshToken(1L);

        mockMvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /**
     * 저장된 활성 사용자의 정상 접근 토큰으로 각 보호 경로가 200인지 확인.
     */
    @ParameterizedTest(name = "{0} accepts valid token")
    @MethodSource("protectedGetEndpoints")
    void validToken(String endpoint) throws Exception {
        User user = createUser("validUser" + endpointName(endpoint));
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());

        assertProtectedGetSucceeds(endpoint, accessToken);
    }

    /**
     * 토큰 발급 뒤 탈퇴한 사용자의 기존 접근 토큰도 무효 처리되는지 확인.
     */
    @Test
    void withdrawnUserToken() throws Exception {
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

    /**
     * 토큰 발급 뒤 영구 제재된 사용자의 접근이 INVALID_TOKEN으로 거절되는지 확인.
     */
    @Test
    void permanentBanToken() throws Exception {
        User user = createUser("permanentBanMatrixUser");
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());

        user.ban("영구 밴 테스트", LocalDateTime.now(clock));
        userRepository.saveAndFlush(user);

        assertInvalidToken("/users/me", accessToken);
    }

    /**
     * 만료 시점이 미래인 임시 제재 사용자의 기존 접근 토큰이 차단되는지 확인.
     */
    @Test
    void activeTemporaryBanToken() throws Exception {
        User user = createUser("temporaryBanMatrixUser");
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        LocalDateTime now = LocalDateTime.now(clock);

        user.ban("임시 밴 테스트", now, now.plusDays(1));
        userRepository.saveAndFlush(user);

        assertInvalidToken("/users/me", accessToken);
    }

    /**
     * 임시 제재 만료 시점이 지난 사용자는 기존 접근 토큰으로 사용자 정보를 조회할 수 있는지 확인.
     */
    @Test
    void expiredTemporaryBanToken() throws Exception {
        User user = createUser("expiredTemporaryBanMatrixUser");
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        LocalDateTime now = LocalDateTime.now(clock);

        user.ban("만료된 임시 밴 테스트", now.minusDays(2), now.minusDays(1));
        userRepository.saveAndFlush(user);

        assertProtectedGetSucceeds("/users/me", accessToken);
    }

    /**
     * ERROR dispatch에서는 원래의 500 오류를 유지하고 인증 오류 코드로 덮어쓰지 않는지 확인.
     */
    @Test
    void errorDispatch() throws Exception {
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

    /**
     * 장소 목록·정보 제보·내 정보의 공통 인증 경계를 확인할 GET 경로를 제공.
     */
    private static Stream<String> protectedGetEndpoints() {
        return Stream.of("/places", "/places/information-reports", "/users/me");
    }

    /**
     * 접근 토큰을 Bearer 헤더에 넣어 지정 경로의 200 응답을 확인.
     */
    private void assertProtectedGetSucceeds(String endpoint, String accessToken) throws Exception {
        mockMvc.perform(get(endpoint)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    /**
     * 지정 보호 경로에서 401과 INVALID_TOKEN을 함께 확인.
     */
    private void assertInvalidToken(String endpoint, String accessToken) throws Exception {
        mockMvc.perform(get(endpoint)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /**
     * 실제 설정된 비밀키로 서명하되 60초 전에 만료된 access 유형 토큰을 만들어 만료 검증을 분리.
     */
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

    /**
     * 원래 서명은 유지한 채 payload 첫 글자만 바꿔 서명 불일치를 생성.
     */
    private String tamper(String token) {
        String[] parts = token.split("\\.", 3);
        char firstPayloadChar = parts[1].charAt(0);
        char replacement = firstPayloadChar == 'a' ? 'b' : 'a';
        return parts[0] + "." + replacement + parts[1].substring(1) + "." + parts[2];
    }

    /**
     * 경로의 슬래시와 하이픈을 제거해 매개변수별 사용자명 접미사로 사용.
     */
    private String endpointName(String endpoint) {
        return endpoint.replace("/", "").replace("-", "");
    }
}
