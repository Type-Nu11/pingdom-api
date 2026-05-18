package com.typenull.pingdom.domain.firebase.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {

    NEW_HOTPLACE("새로운 핫플레이스!", "%s이(가) 근처에 등록됐어요"),
    NEW_LIKE("좋아요 알림", "%s님이 좋아요를 눌렀어요");

    private final String title;
    private final String bodyTemplate;

    public String formatBody(String... args) {
        return String.format(bodyTemplate, (Object[]) args);
    }
}