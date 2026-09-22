package com.typenull.pingdom.shared.security.jwt;

import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bearer access token과 계정 상태가 유효할 때 SecurityContext에 사용자·역할을 설정.
 * 유효하지 않은 요청도 다음 필터로 전달하며 최종 공개 경로·인증 요구는 보안 체인이 결정. */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    static final String ACCESS_TOKEN_EXPIRED_ATTRIBUTE = "ACCESS_TOKEN_EXPIRED";
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

    /** 인증·오류·상태 확인·API 문서 경로는 JWT 해석을 생략. 전체 접근 허용 규칙은 별도 설정에 따름. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return EXCLUDED_PATH_PATTERNS.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, request.getRequestURI()));
    }

    /** 만료 토큰은 요청 속성으로 표시하고, 유효 토큰만 계정 상태를 확인. role이 없으면 빈 권한 목록을 사용. */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
        String accessToken = resolveAccessToken(authorizationHeader);

        if (accessToken != null) {
            JwtTokenProvider.AccessTokenParseResult parsed = jwtTokenProvider.parseAccessToken(accessToken);
            JwtTokenProvider.TokenStatus status = parsed.status();

            if (status == JwtTokenProvider.TokenStatus.EXPIRED) {
                request.setAttribute(ACCESS_TOKEN_EXPIRED_ATTRIBUTE, true);
            }

            if (status == JwtTokenProvider.TokenStatus.VALID && parsed.payload() != null) {
                boolean canAuthenticate = userAccessStatusService.canAuthenticate(parsed.payload().userId());
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
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    // Authorization 헤더에서 Access Token 추출 메서드
    private String resolveAccessToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }

        return authorizationHeader.substring(BEARER_PREFIX.length());
    }

}
