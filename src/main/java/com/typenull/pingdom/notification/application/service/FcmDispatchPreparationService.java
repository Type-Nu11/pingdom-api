package com.typenull.pingdom.notification.application.service;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.domain.FcmDeviceToken;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.domain.Notifications;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationsRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 외부 전송 전에 사용자·정책·기기를 확인하고 재시작에도 재사용할 앱 알림을 커밋합니다. */
@Service
@RequiredArgsConstructor
public class FcmDispatchPreparationService {

    private final UserRepository userRepository;
    private final NotificationsRepository notificationsRepository;
    private final FcmDeviceTokenService fcmDeviceTokenService;
    private final NotificationDeliveryPolicy notificationDeliveryPolicy;
    private final NotificationDeliveryRecorder notificationDeliveryRecorder;
    private final Clock clock;

    @Transactional
    public Optional<PreparedNotification> prepareLike(Long ownerId, Long likerId, String outboxEventId) {
        if (Objects.equals(ownerId, likerId)) {
            return Optional.empty();
        }
        User owner = userRepository.findById(ownerId).orElse(null);
        if (owner == null || owner.isWithdrawn()) {
            return Optional.empty();
        }
        User liker = userRepository.findById(likerId).orElse(null);
        if (liker == null || liker.isWithdrawn()) {
            return Optional.empty();
        }
        return prepare(ownerId, NotificationType.NEW_LIKE, outboxEventId, liker.getUsername());
    }

    @Transactional
    public Optional<PreparedNotification> preparePlaceInformationReverification(
            Long userId, NotificationType type, String placeName, String outboxEventId
    ) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.isWithdrawn()) {
            return Optional.empty();
        }
        if (type != NotificationType.PLACE_INFORMATION_REVERIFICATION_REQUESTED
                && type != NotificationType.PLACE_INFORMATION_REVERIFICATION_REMINDER) {
            throw new IllegalArgumentException("지원하지 않는 장소 정보 재확인 알림 유형입니다.");
        }
        return prepare(userId, type, outboxEventId, placeName);
    }

    private Optional<PreparedNotification> prepare(
            Long userId, NotificationType type, String outboxEventId, String... args
    ) {
        if (!notificationDeliveryPolicy.canReceive(userId, type)) {
            return Optional.empty();
        }
        List<String> tokens = fcmDeviceTokenService.findTokens(userId).stream()
                .map(FcmDeviceToken::getToken)
                .toList();
        if (tokens.isEmpty()) {
            return Optional.empty();
        }

        Notifications notification = resolveNotification(userId, type, outboxEventId, args);
        return Optional.of(new PreparedNotification(userId, type, notification.getTitle(), notification.getBody(),
                notification.getId(), outboxEventId, tokens));
    }

    private Notifications resolveNotification(
            Long userId, NotificationType type, String outboxEventId, String... args
    ) {
        String title = type.getTitle();
        String body = type.formatBody(args);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!StringUtils.hasText(outboxEventId)) {
            return notificationsRepository.save(Notifications.builder()
                    .userId(userId).type(type).title(title).body(body).isRead(false).createdAt(now).build());
        }

        String eventKey = "fcm:" + outboxEventId;
        Optional<Notifications> prepared = notificationsRepository.findByUserIdAndEventKey(userId, eventKey);
        if (prepared.isPresent()) {
            return requireType(prepared.get(), type);
        }

        // 기존 발송 기록이 가리키는 정상 알림은 재사용하되 삭제·다른 사용자/유형의 알림은 신뢰하지 않습니다.
        Long legacyId = notificationDeliveryRecorder.findFcmNotificationId(outboxEventId);
        if (legacyId != null) {
            Optional<Notifications> legacy = notificationsRepository
                    .findByIdAndUserIdAndTypeIn(legacyId, userId, List.of(type));
            if (legacy.isPresent()) {
                return legacy.get();
            }
        }

        // 전송 기록이 생기기 전 프로세스가 종료돼도 event_key의 유일 제약으로 같은 알림을 찾습니다.
        notificationsRepository.insertFcmNotificationIfAbsent(userId, type.name(), title, body, eventKey, now);
        return requireType(notificationsRepository.findByUserIdAndEventKey(userId, eventKey)
                .orElseThrow(() -> new IllegalStateException("FCM 전송 준비 알림을 찾을 수 없습니다.")), type);
    }

    private Notifications requireType(Notifications notification, NotificationType type) {
        if (notification.getType() != type) {
            throw new IllegalStateException("같은 FCM 이벤트의 알림 유형이 일치하지 않습니다.");
        }
        return notification;
    }

    /** JPA 엔티티와 가변 컬렉션을 트랜잭션 밖으로 전달하지 않습니다. */
    public record PreparedNotification(
            Long userId,
            NotificationType type,
            String title,
            String body,
            Long notificationId,
            String outboxEventId,
            List<String> tokens
    ) {
        public PreparedNotification {
            tokens = List.copyOf(tokens);
        }
    }
}
