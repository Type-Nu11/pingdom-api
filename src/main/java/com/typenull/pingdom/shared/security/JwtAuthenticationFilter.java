package com.typenull.pingdom.shared.security;

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

// Authorization 헤더의 Bearer 토큰을 인증 객체로 변환하는 필터
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    static final String ACCESS_TOKEN_EXPIRED_ATTRIBUTE = "ACCESS_TOKEN_EXPIRED";
    private static final List<String> EXCLUDED_PATH_PATTERNS = List.of(
            "/auth/**",
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
        String accessToken = resolveAccessToken(request);

        if (accessToken != null) {
            JwtTokenProvider.AccessTokenParseResult parsed = jwtTokenProvider.parseAccessToken(accessToken);
            JwtTokenProvider.TokenStatus status = parsed.status();

            if (status == JwtTokenProvider.TokenStatus.EXPIRED) {
                request.setAttribute(ACCESS_TOKEN_EXPIRED_ATTRIBUTE, true);
            }

            if (status == JwtTokenProvider.TokenStatus.VALID
                    && parsed.payload() != null
                    && userAccessStatusService.canAuthenticate(parsed.payload().userId())) {
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

        filterChain.doFilter(request, response);
    }

    // Authorization 헤더에서 Access Token 추출 메서드
    private String resolveAccessToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }

        return authorizationHeader.substring(BEARER_PREFIX.length());
    }

}
