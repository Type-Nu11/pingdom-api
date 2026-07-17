package com.typenull.pingdom.identity.application.query;

import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.repository.TravelScheduleRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceClaimRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.infrastructure.crypto.MerchantVerificationCipher;
import com.typenull.pingdom.identity.domain.repository.UserCurrentActivityIntentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.domain.travel.TravelSchedule;
import com.typenull.pingdom.offer.infrastructure.TouristCouponRepository;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import com.typenull.pingdom.privacy.event.PrivacyProcessingEvent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDataExportService {

    private static final int LIKE_EXPORT_LIMIT = 50;

    private final UserRepository userRepository;
    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapImageLikeRepository mapImageLikeRepository;
    private final TravelScheduleRepository travelScheduleRepository;
    private final UserCurrentActivityIntentRepository currentActivityIntentRepository;
    private final MerchantOwnerProfileRepository merchantOwnerProfileRepository;
    private final MerchantOwnerPlaceRepository merchantOwnerPlaceRepository;
    private final MerchantPlaceClaimRepository merchantPlaceClaimRepository;
    private final MerchantVerificationRepository merchantVerificationRepository;
    private final TouristOfferRepository touristOfferRepository;
    private final TouristCouponRepository touristCouponRepository;
    private final MerchantVerificationCipher merchantVerificationCipher;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public UserDataExportResult exportMyData(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsersException(UsersErrorCode.USER_NOT_FOUND));

        List<UserDataExportResult.ExportBookmark> bookmarks = mapBookmarkRepository.findByUserIdOrderByIdAsc(userId)
                .stream()
                .map(bookmark -> new UserDataExportResult.ExportBookmark(
                        bookmark.getId(),
                        bookmark.getPlaceId()
                ))
                .toList();

        List<Long> likedMapImageIds = mapImageLikeRepository.findRecentMapImageIdsByUserId(
                userId,
                PageRequest.of(0, LIKE_EXPORT_LIMIT)
        );
        List<TravelSchedule> travelSchedules = travelScheduleRepository
                .findAllByUser_IdOrderByStartDateAscIdAsc(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        var currentActivityIntent = currentActivityIntentRepository.findByUser_Id(userId)
                .filter(intent -> intent.isActiveAt(now))
                .orElse(null);
        var merchantOwnerProfile = merchantOwnerProfileRepository.findById(userId).orElse(null);
        List<Long> merchantOwnerPlaceIds = merchantOwnerPlaceRepository
                .findAllByMerchantOwnerUserIdOrderByPlaceIdAsc(userId)
                .stream()
                .map(place -> place.getPlaceId())
                .toList();
        var merchantPlaceClaims = merchantPlaceClaimRepository
                .findAllByMerchantOwnerUserIdOrderByCreatedAtDescIdDesc(userId);
        var merchantVerification = merchantVerificationRepository.findById(userId).orElse(null);
        var touristOffers = touristOfferRepository
                .findAllByMerchantOwnerUserIdOrderByCreatedAtDescIdDesc(userId);
        var touristCoupons = touristCouponRepository.findAllByUserIdOrderByIssuedAtDescIdDesc(userId);

        eventPublisher.publishEvent(PrivacyProcessingEvent.userAction(
                userId,
                PrivacyProcessingAction.EXPORT_REQUESTED,
                "사용자 데이터 export 요청"
        ));
        return UserDataExportResult.of(
                user,
                bookmarks,
                likedMapImageIds,
                travelSchedules,
                currentActivityIntent,
                merchantOwnerProfile,
                merchantOwnerPlaceIds,
                merchantPlaceClaims,
                merchantVerification,
                touristOffers,
                touristCoupons,
                now,
                merchantVerification == null
                        ? null
                        : merchantVerificationCipher.decrypt(
                                merchantVerification.getEncryptedBusinessRegistrationNumber()
                        )
        );
    }
}
