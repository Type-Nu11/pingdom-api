package com.typenull.pingdom.shared.ratelimit;

import com.typenull.pingdom.identity.api.dto.email.EmailResendRequest;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.passwordreset.PasswordResetConfirmRequest;
import com.typenull.pingdom.identity.api.dto.passwordreset.PasswordResetRequest;
import com.typenull.pingdom.identity.api.dto.token.RefreshTokenRequest;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.shared.security.JwtAuthenticatedUser;
import com.typenull.pingdom.shared.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class RateLimitAspect {

    private final AbuseRateLimitService abuseRateLimitService;

    public RateLimitAspect(AbuseRateLimitService abuseRateLimitService) {
        this.abuseRateLimitService = abuseRateLimitService;
    }

    @Before("@annotation(rateLimited)")
    public void applyRateLimit(JoinPoint joinPoint, RateLimited rateLimited) {
        String clientIp = ClientIpResolver.resolve(currentRequest());
        Object[] args = joinPoint.getArgs();

        switch (rateLimited.value()) {
            case LOGIN -> {
                LoginRequest request = requiredArg(args, LoginRequest.class);
                abuseRateLimitService.checkLogin(request.username(), clientIp);
            }
            case TOKEN_REFRESH -> {
                RefreshTokenRequest request = requiredArg(args, RefreshTokenRequest.class);
                abuseRateLimitService.checkTokenRefresh(request.refreshToken(), clientIp);
            }
            case EMAIL_RESEND -> {
                EmailResendRequest request = requiredArg(args, EmailResendRequest.class);
                abuseRateLimitService.checkEmailResend(request.email(), clientIp);
            }
            case PASSWORD_RESET_REQUEST -> {
                PasswordResetRequest request = requiredArg(args, PasswordResetRequest.class);
                abuseRateLimitService.checkPasswordResetRequest(request.email(), clientIp);
            }
            case PASSWORD_RESET_CONFIRM -> {
                PasswordResetConfirmRequest request = requiredArg(args, PasswordResetConfirmRequest.class);
                abuseRateLimitService.checkPasswordResetConfirm(request.token(), clientIp);
            }
            case POST_REPORT -> abuseRateLimitService.checkPostReport(requiredUser(args).userId(), clientIp);
            case MAP_IMAGE_LIKE -> abuseRateLimitService.checkMapImageLike(requiredUser(args).userId(), clientIp);
            case RECOMMENDATION_CLICK -> abuseRateLimitService.checkRecommendationClick(requiredUser(args).userId(), clientIp);
            case IMAGE_UPLOAD -> abuseRateLimitService.checkImageUpload(requiredUser(args).userId(), clientIp);
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private JwtAuthenticatedUser requiredUser(Object[] args) {
        JwtAuthenticatedUser user = findArg(args, JwtAuthenticatedUser.class);
        if (user == null) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }
        return user;
    }

    private <T> T requiredArg(Object[] args, Class<T> type) {
        T arg = findArg(args, type);
        if (arg == null) {
            throw new IllegalStateException("Rate limit 대상 인자를 찾을 수 없습니다. type=" + type.getSimpleName());
        }
        return arg;
    }

    private <T> T findArg(Object[] args, Class<T> type) {
        for (Object arg : args) {
            if (type.isInstance(arg)) {
                return type.cast(arg);
            }
        }
        return null;
    }
}
