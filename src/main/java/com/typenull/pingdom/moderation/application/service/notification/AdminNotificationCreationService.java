package com.typenull.pingdom.moderation.application.service.notification;

import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationsRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 알림 유형에 맞는 현재 권한 보유 관리자에게 DB 알림 생성.
 * 이벤트 키·사용자 조합의 충돌은 insert-if-absent로 생략하고 실제 신규 삽입 행 수 반환. FCM 발송은 처리 범위에서 제외.
 */
@Service
@RequiredArgsConstructor
public class AdminNotificationCreationService {

    private final AdminNotificationRecipientResolver recipientResolver;
    private final NotificationsRepository notificationsRepository;
    private final Clock clock;

    /**
     * 관리자 알림 유형·이벤트 키·토큰·본문 인수 목록을 검증한 뒤 현재 권한 보유 수신자의 DB 알림을 생성.
     * 유효하지 않은 입력은 IllegalArgumentException이며 이벤트 키·사용자 중복 삽입을 건너뛴 실제 새 행 수를 반환.
     */
    @Transactional
    public int create(
            NotificationType type,
            String eventKey,
            String token,
            List<String> bodyArguments
    ) {
        validate(type, eventKey, token, bodyArguments);
        String body = type.formatBody(bodyArguments.toArray(String[]::new));
        LocalDateTime createdAt = LocalDateTime.now(clock);

        return recipientResolver.resolve(type).stream()
                .mapToInt(userId -> notificationsRepository.insertAdminNotificationIfAbsent(
                        userId,
                        type.name(),
                        type.getTitle(),
                        body,
                        token,
                        eventKey,
                        createdAt
                ))
                .sum();
    }

    private void validate(
            NotificationType type,
            String eventKey,
            String token,
            List<String> bodyArguments
    ) {
        if (type == null
                || !type.isAdminType()
                || !StringUtils.hasText(eventKey)
                || !StringUtils.hasText(token)
                || bodyArguments == null) {
            throw new IllegalArgumentException("관리자 알림 생성 정보가 올바르지 않습니다.");
        }
    }
}
