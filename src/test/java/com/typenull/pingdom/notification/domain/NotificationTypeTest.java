package com.typenull.pingdom.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationTypeTest {

    @Test
    void formatsAdminNotificationBodiesWithTargetIdentifiers() {
        assertThat(NotificationType.ADMIN_REPORT_RECEIVED.formatBody("30", "12"))
                .isEqualTo("신고 ID 30 접수가 게시글 ID 12에 등록되었습니다.");
        assertThat(NotificationType.ADMIN_REPORT_PROCESSED.formatBody("12", "30", "수락"))
                .isEqualTo("게시글 ID 12의 신고 ID 30 처리가 수락으로 완료되었습니다.");
        assertThat(NotificationType.ADMIN_DUPLICATE_PLACE_DETECTED.formatBody("7", "8"))
                .isEqualTo("장소 ID 7, 8 조합이 중복 후보로 탐지되었습니다.");
        assertThat(NotificationType.ADMIN_USER_SANCTION.formatBody("9", "만료", "50"))
                .isEqualTo("사용자 ID 9의 제재 상태가 만료되었습니다. (제재 ID: 50)");
    }
}
