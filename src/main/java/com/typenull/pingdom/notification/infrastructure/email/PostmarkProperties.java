package com.typenull.pingdom.notification.infrastructure.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Postmark 메일 발송 설정 프로퍼티
@Validated
@ConfigurationProperties(prefix = "postmark")
public record PostmarkProperties(
        @NotBlank(message = "Postmark 서버 토큰은 필수입니다.")
        String serverToken,

        @NotBlank(message = "Postmark 발신자 이메일은 필수입니다.")
        @Email(message = "Postmark 발신자 이메일 형식이 올바르지 않습니다.")
        String fromEmail,

        @NotBlank(message = "인증 메일 기본 URL은 필수입니다.")
        String verificationBaseUrl,

        String passwordResetBaseUrl
) {
    public PostmarkProperties {
        serverToken = normalize(serverToken);
        fromEmail = normalize(fromEmail);
        verificationBaseUrl = normalize(verificationBaseUrl);
        passwordResetBaseUrl = normalize(passwordResetBaseUrl);

        if (verificationBaseUrl == null) {
            verificationBaseUrl = "http://localhost:8080/auth/email/verify";
        }
        if (passwordResetBaseUrl == null) {
            passwordResetBaseUrl = "http://localhost:8080/auth/password-reset/confirm";
        }
    }

    public String validatedFromEmail() {
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new PostmarkConfigurationException("postmark.from-email 설정이 필요합니다.");
        }
        return fromEmail;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
