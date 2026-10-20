package com.typenull.pingdom.shared.security.handler;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 미등록 페이지 요청을 JWT 인증 전에 콘솔 로그인으로 안내. 등록된 API의 인증 정책은 유지. */
public class UnknownPathRedirectFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/console/login";
    private static final String[] RESERVED_PATHS = {
            "/console/**", "/error", "/actuator/**",
            "/swagger-ui", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"
    };
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final Supplier<RequestMappingHandlerMapping> handlerMapping;

    /** MVC 초기화와 Security 설정의 순환 참조를 피하기 위해 요청 시점에 mapping을 조회. */
    public UnknownPathRedirectFilter(Supplier<RequestMappingHandlerMapping> handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!HttpMethod.GET.matches(request.getMethod()) && !HttpMethod.HEAD.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        PathContainer path = RequestPath.parse(request.getRequestURI(), request.getContextPath())
                .pathWithinApplication();
        for (String reserved : RESERVED_PATHS) {
            // 콘솔은 프론트에서 제공. 로그인 재진입과 MVC 외부의 관리·문서 경로는 기존 처리를 유지.
            if (PATH_MATCHER.match(reserved, path.value())) {
                filterChain.doFilter(request, response);
                return;
            }
        }
        for (RequestMappingInfo mapping : handlerMapping.get().getHandlerMethods().keySet()) {
            // HTTP method·Content-Type은 무시하고 경로만 확인해 기존 API의 401/403/405 처리를 보존.
            if (matchesPath(mapping, path)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // 상대 Location을 사용해 프록시 내부 HTTP 주소·포트가 응답에 노출되는 것을 방지.
        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader(HttpHeaders.LOCATION, LOGIN_PATH);
    }

    private boolean matchesPath(RequestMappingInfo mapping, PathContainer path) {
        if (mapping.getPathPatternsCondition() != null) {
            return mapping.getPathPatternsCondition().getPatterns().stream()
                    .anyMatch(pattern -> pattern.matches(path));
        }
        return mapping.getPatternsCondition() != null
                && !mapping.getPatternsCondition().getMatchingPatterns(path.value()).isEmpty();
    }
}
