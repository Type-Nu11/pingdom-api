package com.typenull.pingdom.notification.infrastructure.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 시작 시 Postmark 필수 문자열 설정을 검사하고 누락되면 애플리케이션 초기화를 실패시킴.
 * 서버 토큰의 실제 권한, 발신 도메인 인증 상태, 링크의 접속 가능 여부에 대한 외부 호출 검증은 범위 외.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostmarkConfigurationVerifier {

    private final PostmarkProperties postmarkProperties;

    @jakarta.annotation.PostConstruct
    void verify() {
        try {
            validateRequired("postmark.server-token", postmarkProperties.serverToken());
            validateRequired("postmark.from-email", postmarkProperties.validatedFromEmail());
            validateRequired("postmark.verification-base-url", postmarkProperties.verificationBaseUrl());
            validateRequired("postmark.password-reset-base-url", postmarkProperties.passwordResetBaseUrl());
            log.info("Postmark email sender configured. fromEmail={}", postmarkProperties.validatedFromEmail());
        } catch (PostmarkConfigurationException exception) {
            log.error("Invalid Postmark email sender configuration: {}", exception.getMessage());
            throw exception;
        }
    }

    private void validateRequired(String propertyName, String value) {
        if (!StringUtils.hasText(value)) {
            throw new PostmarkConfigurationException(propertyName + " 설정이 필요합니다.");
        }
    }
}
