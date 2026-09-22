package com.typenull.pingdom.shared.outbox.application;

import com.typenull.pingdom.notification.outbox.EmailVerificationOutboxPayload;

final class OutboxEventContractFixture {

    static final String AGGREGATE_TYPE = "USER";
    static final String AGGREGATE_ID = "100";
    static final String RECIPIENT_EMAIL = "outbox-contract@pingdom.test";
    static final String VERIFICATION_CODE = "123456";

    /**
     * 이메일 Outbox 통합 검증의 고정 입력을 정적 메서드로 제공하므로 인스턴스화를 막는다.
     */
    private OutboxEventContractFixture() {
    }

    /**
     * 고정 사용자 집계 ID와 사례별 접미사를 결합해 이메일 이벤트의 중복 방지 키를 만든다.
     */
    static String deduplicationKey(String suffix) {
        return "EMAIL_VERIFICATION:" + AGGREGATE_ID + ":" + suffix;
    }

    /**
     * 고정 집계 사용자·수신 이메일·인증 코드를 payload로 묶어 발행부터 핸들러 전달까지 비교한다.
     */
    static EmailVerificationOutboxPayload emailVerificationPayload() {
        return new EmailVerificationOutboxPayload(Long.valueOf(AGGREGATE_ID), RECIPIENT_EMAIL, VERIFICATION_CODE);
    }
}
