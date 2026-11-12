package com.typenull.pingdom.notification.application.service;

import com.typenull.pingdom.notification.api.dto.fcm.NotificationResponse;
import com.typenull.pingdom.notification.domain.NotificationType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커밋된 전송 준비값으로 FCM을 전송하고 결과를 기록합니다.
 * 상위 트랜잭션을 허용하면 중단된 트랜잭션의 커넥션도 남을 수 있으므로 진입 자체를 거절합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.NEVER)
public class FcmService {

    private final FcmDispatchPreparationService preparationService;
    private final FcmDeviceTokenService fcmDeviceTokenService;
    private final FcmMessageSender fcmMessageSender;
    private final NotificationDeliveryRecorder notificationDeliveryRecorder;

    public NotificationResponse sendLikeNotification(Long ownerId, Long likerId) {
        return sendLikeNotification(ownerId, likerId, null).notification();
    }

    public FcmDispatchResult sendLikeNotification(Long ownerId, Long likerId, String outboxEventId) {
        return dispatch(preparationService.prepareLike(ownerId, likerId, outboxEventId));
    }

    public FcmDispatchResult sendPlaceInformationReverificationNotification(
            Long userId, NotificationType type, String placeName, String outboxEventId
    ) {
        return dispatch(preparationService.preparePlaceInformationReverification(userId, type, placeName, outboxEventId));
    }

    /** 준비 트랜잭션이 커밋된 값만 전송하며, 성공 기록을 조회하는 짧은 트랜잭션도 전송 전에 종료합니다. */
    private FcmDispatchResult dispatch(Optional<FcmDispatchPreparationService.PreparedNotification> prepared) {
        if (prepared.isEmpty()) {
            return FcmDispatchResult.skipped();
        }
        FcmDispatchPreparationService.PreparedNotification notification = prepared.get();
        boolean hasRetryableFailure = false;
        for (String token : notification.tokens()) {
            if (notificationDeliveryRecorder.isFcmDeliverySucceeded(notification.outboxEventId(), token)) {
                log.info("이미 성공한 FCM 토큰 전송을 재시도에서 생략합니다. type={}, eventId={}",
                        notification.type(), notification.outboxEventId());
                continue;
            }
            hasRetryableFailure |= sendToToken(notification.userId(), token, notification.type(),
                    notification.title(), notification.body(), notification.notificationId(), notification.outboxEventId());
        }
        return new FcmDispatchResult(new NotificationResponse(notification.notificationId()), hasRetryableFailure);
    }

    /**
     * 무효 토큰은 실패를 기록하고 삭제하여 재시도 대상에서 제외.
     * 그 외 실패는 다른 토큰 처리를 계속하면서 재시도 필요를 반환하며, 결과 기록 실패는 Recorder가 별도로 흡수.
     */
    private boolean sendToToken(
            Long userId,
            String token,
            NotificationType type,
            String title,
            String body,
            Long notificationId,
            String outboxEventId
    ) {
        try {
            String response = fcmMessageSender.send(token, type, title, body, notificationId);
            notificationDeliveryRecorder.recordFcmSuccess(userId, notificationId, type, outboxEventId, token, response);
            log.info("FCM 전송 성공: {}", response);
            return false;
        } catch (FcmSendException exception) {
            if (exception.isInvalidToken()) {
                notificationDeliveryRecorder.recordFcmFailure(
                        userId,
                        notificationId,
                        type,
                        outboxEventId,
                        token,
                        exception.getProviderErrorCode(),
                        NotificationDeliveryRecorder.ERROR_FCM_INVALID_TOKEN,
                        exception.getMessage(),
                        false
                );
                fcmDeviceTokenService.deleteInvalidToken(token);
                log.warn("무효 FCM 토큰을 삭제했습니다. type={}, reason={}", type, exception.getMessage());
                return false;
            }
            notificationDeliveryRecorder.recordFcmFailure(
                    userId,
                    notificationId,
                    type,
                    outboxEventId,
                    token,
                    exception.getProviderErrorCode(),
                    NotificationDeliveryRecorder.ERROR_FCM_SEND_FAILED,
                    exception.getMessage(),
                    true
            );
            log.error(
                    "FCM 개별 토큰 전송 실패 - userId={}, type={}, reason={}",
                    userId,
                    type,
                    exception.getMessage(),
                    exception
            );
            return true;
        } catch (RuntimeException exception) {
            notificationDeliveryRecorder.recordFcmFailure(
                    userId,
                    notificationId,
                    type,
                    outboxEventId,
                    token,
                    null,
                    NotificationDeliveryRecorder.ERROR_FCM_SEND_FAILED,
                    exception.getMessage(),
                    true
            );
            log.error(
                    "FCM 개별 토큰 전송 실패 - userId={}, type={}, reason={}",
                    userId,
                    type,
                    exception.getMessage(),
                    exception
            );
            return true;
        }
    }
}
