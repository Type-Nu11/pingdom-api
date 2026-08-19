package com.typenull.pingdom.notification.domain;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {

    NEW_HOTPLACE("새로운 핫플레이스!", "%s이(가) 근처에 등록됐어요"),
    NEW_LIKE("좋아요 알림", "%s님이 좋아요를 눌렀어요"),
    PLACE_INFORMATION_REVERIFICATION_REQUESTED("장소 정보 재확인 요청", "%s 장소 정보를 재확인해 주세요"),
    PLACE_INFORMATION_REVERIFICATION_REMINDER("장소 정보 재확인 리마인드", "%s 장소의 재확인 기한이 다가오고 있습니다"),
    ADMIN_REPORT_RECEIVED("신고 접수 알림", "신고 ID %s 접수가 게시글 ID %s에 등록되었습니다."),
    ADMIN_REPORT_PROCESSED("신고 처리 알림", "게시글 ID %s의 신고 ID %s 처리가 %s으로 완료되었습니다."),
    ADMIN_DUPLICATE_PLACE_DETECTED("중복 장소 알림", "장소 ID %s, %s 조합이 중복 후보로 탐지되었습니다."),
    ADMIN_USER_SANCTION("사용자 제재 알림", "사용자 ID %s의 제재 상태가 %s되었습니다. (제재 ID: %s)");

    private static final List<NotificationType> ADMIN_TYPES = Arrays.stream(values())
            .filter(NotificationType::isAdminType)
            .toList();

    private final String title;
    private final String bodyTemplate;

    public String formatBody(String... args) {
        return String.format(bodyTemplate, (Object[]) args);
    }

    public boolean isAdminType() {
        return name().startsWith("ADMIN_");
    }

    public static List<NotificationType> adminTypes() {
        return ADMIN_TYPES;
    }
}
