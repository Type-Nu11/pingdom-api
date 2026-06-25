package com.typenull.pingdom.notification.infrastructure.email;

import com.postmarkapp.postmark.client.ApiClient;
import com.postmarkapp.postmark.client.data.model.message.Message;
import com.postmarkapp.postmark.client.exception.PostmarkException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.typenull.pingdom.identity.application.port.EmailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// Postmark 기반 인증 메일 발송 구현 클래스
@Component
public class PostmarkEmailSender implements EmailSender {

    private static final String POSTMARK_API_HOST = "api.postmarkapp.com";
    private static final String POSTMARK_SERVER_TOKEN_HEADER = "X-Postmark-Server-Token";
    private static final String VERIFICATION_SUBJECT = "Pingdom 이메일 인증";
    private static final String PASSWORD_RESET_SUBJECT = "Pingdom 비밀번호 재설정";
    private static final DateTimeFormatter PASSWORD_RESET_EXPIRATION_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PostmarkProperties postmarkProperties;
    private final ApiClient apiClient;

    public PostmarkEmailSender(PostmarkProperties postmarkProperties) {
        this.postmarkProperties = postmarkProperties;
        this.apiClient = new ApiClient(
                POSTMARK_API_HOST,
                Map.of(POSTMARK_SERVER_TOKEN_HEADER, postmarkProperties.serverToken())
        );
    }

    @Override
    // 인증 코드 메일 발송 메서드
    public void sendVerificationEmail(String recipientEmail, String verificationCode) {
        validateConfiguration();

        Message message = new Message();
        message.setFrom(postmarkProperties.fromEmail());
        message.setTo(recipientEmail);
        message.setSubject(VERIFICATION_SUBJECT);
        message.setTextBody(buildTextBody(recipientEmail, verificationCode));
        message.setHtmlBody(buildHtmlBody(recipientEmail, verificationCode));

        try {
            apiClient.deliverMessage(message);
        } catch (PostmarkException | IOException exception) {
            throw new IllegalStateException("인증 메일 발송에 실패했습니다.", exception);
        }
    }

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String resetToken, LocalDateTime expiresAt) {
        validateConfiguration();

        Message message = new Message();
        message.setFrom(postmarkProperties.fromEmail());
        message.setTo(recipientEmail);
        message.setSubject(PASSWORD_RESET_SUBJECT);
        message.setTextBody(buildPasswordResetTextBody(recipientEmail, resetToken, expiresAt));
        message.setHtmlBody(buildPasswordResetHtmlBody(recipientEmail, resetToken, expiresAt));

        try {
            apiClient.deliverMessage(message);
        } catch (PostmarkException | IOException exception) {
            throw new IllegalStateException("비밀번호 재설정 메일 발송에 실패했습니다.", exception);
        }
    }

    // 메일 발송 설정 검증 메서드
    private void validateConfiguration() {
        if (!StringUtils.hasText(postmarkProperties.serverToken())) {
            throw new IllegalStateException("POSTMARK_SERVER_TOKEN 설정이 필요합니다.");
        }
        if (!StringUtils.hasText(postmarkProperties.fromEmail())) {
            throw new IllegalStateException("MAIL_FROM 설정이 필요합니다.");
        }
        if (!StringUtils.hasText(postmarkProperties.verificationBaseUrl())) {
            throw new IllegalStateException("MAIL_VERIFICATION_BASE_URL 설정이 필요합니다.");
        }
        if (!StringUtils.hasText(postmarkProperties.passwordResetBaseUrl())) {
            throw new IllegalStateException("MAIL_PASSWORD_RESET_BASE_URL 설정이 필요합니다.");
        }
    }

    // 인증 메일 텍스트 본문 생성 메서드
    private String buildTextBody(String recipientEmail, String verificationCode) {
        return """
                Pingdom 이메일 인증 코드

                인증 코드: %s
                인증 링크: %s
                """.formatted(verificationCode, buildVerificationLink(recipientEmail, verificationCode));
    }

    // 인증 메일 HTML 본문 생성 메서드
    private String buildHtmlBody(String recipientEmail, String verificationCode) {
        return """
                <h2>Pingdom 이메일 인증</h2>
                <p>인증 코드: <strong>%s</strong></p>
                <p><a href="%s">인증 링크 열기</a></p>
                """.formatted(verificationCode, buildVerificationLink(recipientEmail, verificationCode));
    }

    // 인증 링크 생성 메서드
    private String buildVerificationLink(String recipientEmail, String verificationCode) {
        String encodedEmail = URLEncoder.encode(recipientEmail, StandardCharsets.UTF_8);
        String encodedCode = URLEncoder.encode(verificationCode, StandardCharsets.UTF_8);
        return postmarkProperties.verificationBaseUrl()
                + "?email=" + encodedEmail
                + "&code=" + encodedCode;
    }

    private String buildPasswordResetTextBody(String recipientEmail, String resetToken, LocalDateTime expiresAt) {
        String formattedExpiresAt = formatPasswordResetExpiration(expiresAt);
        return """
                Pingdom 비밀번호 재설정

                재설정 링크: %s
                만료 시각: %s
                """.formatted(buildPasswordResetLink(recipientEmail, resetToken), formattedExpiresAt);
    }

    private String buildPasswordResetHtmlBody(String recipientEmail, String resetToken, LocalDateTime expiresAt) {
        String formattedExpiresAt = formatPasswordResetExpiration(expiresAt);
        return """
                <h2>Pingdom 비밀번호 재설정</h2>
                <p><a href="%s">비밀번호 재설정 링크 열기</a></p>
                <p>만료 시각: %s</p>
                """.formatted(buildPasswordResetLink(recipientEmail, resetToken), formattedExpiresAt);
    }

    String buildPasswordResetLink(String recipientEmail, String resetToken) {
        String encodedEmail = URLEncoder.encode(recipientEmail, StandardCharsets.UTF_8);
        String encodedToken = URLEncoder.encode(resetToken, StandardCharsets.UTF_8);
        String baseUrl = postmarkProperties.passwordResetBaseUrl();
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl
                + separator + "email=" + encodedEmail
                + "&token=" + encodedToken;
    }

    private String formatPasswordResetExpiration(LocalDateTime expiresAt) {
        return expiresAt.format(PASSWORD_RESET_EXPIRATION_FORMATTER);
    }
}
