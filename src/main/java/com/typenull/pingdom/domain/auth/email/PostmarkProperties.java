package com.typenull.pingdom.domain.auth.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Postmark 메일 발송 설정 프로퍼티
@ConfigurationProperties(prefix = "postmark")
public record PostmarkProperties(
        String serverToken,

        String fromEmail,

        String verificationBaseUrl
) {
}
