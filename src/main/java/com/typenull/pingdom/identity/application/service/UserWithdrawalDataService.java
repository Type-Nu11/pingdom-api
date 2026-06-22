package com.typenull.pingdom.identity.application.service;

import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.notification.repository.NotificationsRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserWithdrawalDataService {

    private final MapImageRepository mapImageRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final MapImageLikeRepository mapImageLikeRepository;
    private final MapBookmarkRepository mapBookmarkRepository;
    private final NotificationsRepository notificationsRepository;

    @Transactional
    public void cleanupUserOwnedData(Long userId) {
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

        log.info(
                "탈퇴 사용자 연관 데이터를 정리했습니다. userId={}, anonymizedPostCount={}, anonymizedPlaceCount={}, deletedLikeCount={}, deletedBookmarkCount={}, deletedNotificationCount={}",
                userId,
                anonymizedPostCount,
                anonymizedPlaceCount,
                deletedLikeCount,
                deletedBookmarkCount,
                deletedNotificationCount
        );
    }
}
