package com.typenull.pingdom.notification.infrastructure.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
