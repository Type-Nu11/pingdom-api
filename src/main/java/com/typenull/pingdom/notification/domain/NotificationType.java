package com.typenull.pingdom.notification.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {

    NEW_HOTPLACE("새로운 핫플레이스!", "%s이(가) 근처에 등록됐어요"),
    NEW_LIKE("좋아요 알림", "%s님이 좋아요를 눌렀어요"),
    PLACE_INFORMATION_REVERIFICATION_REQUESTED("장소 정보 재확인 요청", "%s 장소 정보를 재확인해 주세요"),
    PLACE_INFORMATION_REVERIFICATION_REMINDER("장소 정보 재확인 리마인드", "%s 장소의 재확인 기한이 다가오고 있습니다");

    private final String title;
    private final String bodyTemplate;

    public String formatBody(String... args) {
        return String.format(bodyTemplate, (Object[]) args);
    }
}
