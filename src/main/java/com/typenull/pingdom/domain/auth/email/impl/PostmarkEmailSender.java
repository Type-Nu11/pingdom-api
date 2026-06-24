package com.typenull.pingdom.domain.auth.email.impl;

import com.postmarkapp.postmark.client.ApiClient;
import com.postmarkapp.postmark.client.data.model.message.Message;
import com.postmarkapp.postmark.client.exception.PostmarkException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.typenull.pingdom.domain.auth.email.EmailSender;
import com.typenull.pingdom.domain.auth.email.PostmarkProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// Postmark 기반 인증 메일 발송 구현 클래스
@Component
public class PostmarkEmailSender implements EmailSender {

    private static final String POSTMARK_API_HOST = "api.postmarkapp.com";
    private static final String POSTMARK_SERVER_TOKEN_HEADER = "X-Postmark-Server-Token";
    private static final String VERIFICATION_SUBJECT = "Pingdom 이메일 인증";

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
}
