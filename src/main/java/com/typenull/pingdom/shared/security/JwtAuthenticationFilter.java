package com.typenull.pingdom.shared.security;

import com.typenull.pingdom.shared.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

// Authorization 헤더의 Bearer 토큰을 인증 객체로 변환하는 필터
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    static final String ACCESS_TOKEN_EXPIRED_ATTRIBUTE = "ACCESS_TOKEN_EXPIRED";
    static final String AUTH_DIAGNOSTIC_ATTRIBUTE = "AUTH_DIAGNOSTIC";
    private static final List<String> EXCLUDED_PATH_PATTERNS = List.of(
            "/auth/**",
            "/error",
            "/actuator/health",
            "/actuator/health/**",
            "/swagger-ui",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    );
    private static final List<String> APPEAL_PATH_PATTERNS = List.of(
            "/map/report-appeals",
            "/map/report-appeals/**"
    );
    // 이슈 #317 임시 진단 로그입니다. 토큰 원문은 남기지 않고, /place 인증 실패 원인이
    // 헤더 누락, 토큰 파싱 실패, 사용자 상태 차단 중 어디인지 확인한 뒤 제거합니다.
    // 남는 위험은 내부 userId가 로그에 노출되는 점입니다.
    private static final List<String> AUTH_DIAGNOSTIC_PATH_PATTERNS = List.of(
            "/place",
            "/place/**",
            "/users/me",
            "/map/posts",
            "/map/bookmarks",
            "/firebase/fcm-token",
            "/firebase/fcm-tokens",
            "/notifications/settings"
    );
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtTokenProvider jwtTokenProvider;
    private final UserAccessStatusService userAccessStatusService;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            UserAccessStatusService userAccessStatusService
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userAccessStatusService = userAccessStatusService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return EXCLUDED_PATH_PATTERNS.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, request.getRequestURI()));
    }

    @Override
    // Access Token 기반 인증 처리 메서드
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
        boolean authHeaderPresent = StringUtils.hasText(authorizationHeader);
        boolean bearerPrefixValid = authHeaderPresent && authorizationHeader.startsWith(BEARER_PREFIX);
        String tokenStatus = resolveMissingTokenStatus(authHeaderPresent, bearerPrefixValid);
        Long tokenUserId = null;
        Boolean canAuthenticateResult = null;
        boolean authenticationSet = false;

        String accessToken = resolveAccessToken(authorizationHeader);

        if (accessToken != null) {
            JwtTokenProvider.AccessTokenParseResult parsed = jwtTokenProvider.parseAccessToken(accessToken);
            JwtTokenProvider.TokenStatus status = parsed.status();
            tokenStatus = status.name();
            if (parsed.payload() != null) {
                tokenUserId = parsed.payload().userId();
            }

            if (status == JwtTokenProvider.TokenStatus.EXPIRED) {
                request.setAttribute(ACCESS_TOKEN_EXPIRED_ATTRIBUTE, true);
            }

            if (status == JwtTokenProvider.TokenStatus.VALID && parsed.payload() != null) {
                boolean canAuthenticate = canAuthenticate(request, parsed.payload().userId());
                canAuthenticateResult = canAuthenticate;
                if (canAuthenticate) {
                    String role = parsed.payload().role();
                    List<SimpleGrantedAuthority> authorities = (role == null)
                            ? Collections.emptyList()
                            : List.of(new SimpleGrantedAuthority("ROLE_" + role));

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    new JwtAuthenticatedUser(parsed.payload().userId(), parsed.payload().username()),
                                    null,
                                    authorities
                            );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    authenticationSet = true;
                }
            }
        }

        recordAuthDiagnostic(
                request,
                requestUri,
                authHeaderPresent,
                bearerPrefixValid,
                tokenStatus,
                tokenUserId,
                canAuthenticateResult,
                authenticationSet
        );
        filterChain.doFilter(request, response);
    }

    // Authorization 헤더에서 Access Token 추출 메서드
    private String resolveAccessToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }

        return authorizationHeader.substring(BEARER_PREFIX.length());
    }

    private boolean canAuthenticate(HttpServletRequest request, Long userId) {
        if (isAppealPath(request)) {
            return userAccessStatusService.canAuthenticateForAppeal(userId);
        }
        return userAccessStatusService.canAuthenticate(userId);
    }

    private boolean isAppealPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return APPEAL_PATH_PATTERNS.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, requestUri));
    }

    private String resolveMissingTokenStatus(boolean authHeaderPresent, boolean bearerPrefixValid) {
        if (!authHeaderPresent) {
            return "MISSING";
        }
        if (!bearerPrefixValid) {
            return "NON_BEARER";
        }
        return "UNPARSED";
    }

    private void recordAuthDiagnostic(
            HttpServletRequest request,
            String requestUri,
            boolean authHeaderPresent,
            boolean bearerPrefixValid,
            String tokenStatus,
            Long userId,
            Boolean canAuthenticate,
            boolean authenticationSet
    ) {
        if (!shouldRecordAuthDiagnostic(requestUri)) {
            return;
        }

        AuthDiagnostic diagnostic = new AuthDiagnostic(
                MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY),
                request.getMethod(),
                requestUri,
                authHeaderPresent,
                bearerPrefixValid,
                tokenStatus,
                userId,
                canAuthenticate,
                authenticationSet
        );
        request.setAttribute(AUTH_DIAGNOSTIC_ATTRIBUTE, diagnostic);

        log.info(
                "auth diagnostic: requestId={} method={} uri={} authHeaderPresent={} bearerPrefixValid={} tokenStatus={} userId={} canAuthenticate={} authenticationSet={}",
                diagnostic.requestId(),
                diagnostic.method(),
                diagnostic.uri(),
                diagnostic.authHeaderPresent(),
                diagnostic.bearerPrefixValid(),
                diagnostic.tokenStatus(),
                diagnostic.userId(),
                diagnostic.canAuthenticate(),
                diagnostic.authenticationSet()
        );
    }

    private boolean shouldRecordAuthDiagnostic(String requestUri) {
        return AUTH_DIAGNOSTIC_PATH_PATTERNS.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, requestUri));
    }

    record AuthDiagnostic(
            String requestId,
            String method,
            String uri,
            boolean authHeaderPresent,
            boolean bearerPrefixValid,
            String tokenStatus,
            Long userId,
            Boolean canAuthenticate,
            boolean authenticationSet
    ) {
    }

}
