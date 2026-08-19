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

@Service
@RequiredArgsConstructor
public class AdminNotificationCreationService {

    private final AdminNotificationRecipientResolver recipientResolver;
    private final NotificationsRepository notificationsRepository;
    private final Clock clock;

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
