package com.typenull.pingdom.identity.application.service.withdrawal;

import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceClaimRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.infrastructure.crypto.MerchantVerificationCipher;
import com.typenull.pingdom.offer.infrastructure.TouristCouponRepository;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import com.typenull.pingdom.notification.repository.FcmDeviceTokenRepository;
import com.typenull.pingdom.notification.repository.NotificationSettingRepository;
import com.typenull.pingdom.notification.repository.NotificationsRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserWithdrawalDataService {

    private static final String ANONYMIZED_BUSINESS_REGISTRATION_NUMBER = "0000000000";

    private final MapImageRepository mapImageRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final MapImageLikeRepository mapImageLikeRepository;
    private final MapBookmarkRepository mapBookmarkRepository;
    private final NotificationsRepository notificationsRepository;
    private final FcmDeviceTokenRepository fcmDeviceTokenRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final MerchantOwnerProfileRepository merchantOwnerProfileRepository;
    private final MerchantOwnerPlaceRepository merchantOwnerPlaceRepository;
    private final MerchantPlaceClaimRepository merchantPlaceClaimRepository;
    private final MerchantVerificationRepository merchantVerificationRepository;
    private final TouristOfferRepository touristOfferRepository;
    private final TouristCouponRepository touristCouponRepository;
    private final MerchantVerificationCipher merchantVerificationCipher;
    private final Clock clock;

    @Transactional
    public void cleanupUserOwnedData(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        int anonymizedPostCount = mapImageRepository.updateUsernameByUserId(
                userId,
                User.WITHDRAWN_DISPLAY_NAME
        );
        int anonymizedPlaceCount = mapPlaceRepository.updateRegistrantByUserId(
                userId,
                User.WITHDRAWN_DISPLAY_NAME
        );
        int deletedLikeCount = mapImageLikeRepository.deleteAllByUserId(userId);
        int deletedBookmarkCount = mapBookmarkRepository.deleteAllByUserId(userId);
        int deletedNotificationCount = notificationsRepository.deleteAllByUserId(userId);
        int deletedFcmTokenCount = fcmDeviceTokenRepository.deleteAllByUserId(userId);
        int deletedNotificationSettingCount = notificationSettingRepository.deleteAllByUserId(userId);
        int deletedMerchantOwnerPlaceCount = merchantOwnerPlaceRepository.deleteAllByMerchantOwnerUserId(userId);
        int deletedMerchantPlaceClaimCount = merchantPlaceClaimRepository.deleteAllByMerchantOwnerUserId(userId);
        int closedTouristOfferCount = touristOfferRepository.closeAllByMerchantOwnerUserId(userId, now);
        int deletedTouristCouponCount = touristCouponRepository.deleteAllByUserId(userId);
        merchantOwnerProfileRepository.findByUserIdForUpdate(userId)
                .ifPresent(profile -> profile.anonymize(now));
        merchantVerificationRepository.findByUserIdForUpdate(userId)
                .ifPresent(verification -> verification.anonymize(
                        merchantVerificationCipher.encrypt(ANONYMIZED_BUSINESS_REGISTRATION_NUMBER),
                        now
                ));

        log.info(
                "탈퇴 사용자 연관 데이터를 정리했습니다. userId={}, anonymizedPostCount={}, anonymizedPlaceCount={}, deletedLikeCount={}, deletedBookmarkCount={}, deletedNotificationCount={}, deletedFcmTokenCount={}, deletedNotificationSettingCount={}, deletedMerchantOwnerPlaceCount={}, deletedMerchantPlaceClaimCount={}, closedTouristOfferCount={}, deletedTouristCouponCount={}",
                userId,
                anonymizedPostCount,
                anonymizedPlaceCount,
                deletedLikeCount,
                deletedBookmarkCount,
                deletedNotificationCount,
                deletedFcmTokenCount,
                deletedNotificationSettingCount,
                deletedMerchantOwnerPlaceCount,
                deletedMerchantPlaceClaimCount,
                closedTouristOfferCount,
                deletedTouristCouponCount
        );
    }

    @Transactional
    public void detachContentUserReferences(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        int detachedPostCount = mapImageRepository.clearUserIdByUserIds(userIds);
        int detachedPlaceCount = mapPlaceRepository.clearUserIdByUserIds(userIds);
        int deletedFcmTokenCount = fcmDeviceTokenRepository.deleteAllByUserIds(userIds);
        int deletedNotificationSettingCount = notificationSettingRepository.deleteAllByUserIds(userIds);

        log.info(
                "최종 삭제 대상 탈퇴 사용자의 콘텐츠 작성자 참조를 제거했습니다. userCount={}, detachedPostCount={}, detachedPlaceCount={}, deletedFcmTokenCount={}, deletedNotificationSettingCount={}",
                userIds.size(),
                detachedPostCount,
                detachedPlaceCount,
                deletedFcmTokenCount,
                deletedNotificationSettingCount
        );
    }
}
