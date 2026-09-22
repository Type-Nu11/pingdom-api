package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerProfileRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerProfileResponse;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 사업자 프로필 신청·재신청·수정을 처리.
 * 상호 변경은 기존 검증을 무효화하며 프로필 수정 경로에서는 진행 중인 혜택도 종료.
 */
@Service
@RequiredArgsConstructor
public class MerchantOwnerProfileService {

    private final UserRepository userRepository;
    private final MerchantOwnerProfileRepository profileRepository;
    private final MerchantOwnerPlaceRepository placeRepository;
    private final MerchantVerificationRepository verificationRepository;
    private final TouristOfferRepository touristOfferRepository;
    private final Clock clock;

    /**
     * 회원·기존 프로필을 잠가 점주 프로필을 신규 신청하거나 재신청하고 연결 장소를 포함한 응답을 반환.
     * ADMIN 신청과 재신청 불가 상태는 거절하며, 재신청으로 상호가 바뀌면 기존 검증 정보를 무효화.
     */
    @Transactional
    public MerchantOwnerProfileResponse apply(Long userId, MerchantOwnerProfileRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UsersException(UsersErrorCode.USER_NOT_FOUND));
        if (user.isAdmin()) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.ADMIN_ACCOUNT_NOT_ALLOWED);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        MerchantOwnerProfile profile = profileRepository.findByUserIdForUpdate(userId).orElse(null);
        if (profile == null) {
            profile = profileRepository.save(MerchantOwnerProfile.pending(
                    userId,
                    request.businessName(),
                    request.displayName(),
                    request.description(),
                    request.contactEmail(),
                    request.contactPhone(),
                    now
            ));
        } else {
            boolean businessNameChanged = !Objects.equals(profile.getBusinessName(), request.businessName());
            try {
                profile.reapply(
                        request.businessName(),
                        request.displayName(),
                        request.description(),
                        request.contactEmail(),
                        request.contactPhone(),
                        now
                );
            } catch (IllegalStateException exception) {
                throw new MerchantOwnerException(MerchantOwnerErrorCode.PROFILE_ALREADY_EXISTS);
            }
            if (businessNameChanged) {
                invalidateVerificationForBusinessNameChange(userId, profile.getBusinessName(), now);
            }
        }
        return response(profile);
    }

    /**
     * 점주 프로필을 잠가 수정 가능한 상태에서 기본 정보를 갱신하고 연결 장소를 포함한 응답을 반환.
     * 프로필 부재·수정 불가 상태는 거절하며, 상호 변경 시 검증 정보를 무효화하고 해당 점주의 혜택을 종료.
     */
    @Transactional
    public MerchantOwnerProfileResponse update(Long userId, MerchantOwnerProfileRequest request) {
        MerchantOwnerProfile profile = requireProfileForUpdate(userId);
        boolean businessNameChanged = !Objects.equals(profile.getBusinessName(), request.businessName());
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            profile.update(
                    request.businessName(),
                    request.displayName(),
                    request.description(),
                    request.contactEmail(),
                    request.contactPhone(),
                    now
            );
        } catch (IllegalStateException exception) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.INVALID_PROFILE_STATE);
        }
        if (businessNameChanged) {
            invalidateVerificationForBusinessNameChange(userId, profile.getBusinessName(), now);
            touristOfferRepository.closeAllByMerchantOwnerUserId(userId, now);
        }
        return response(profile);
    }

    @Transactional(readOnly = true)
    public MerchantOwnerProfileResponse get(Long userId) {
        return response(profileRepository.findById(userId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.PROFILE_NOT_FOUND)));
    }

    private MerchantOwnerProfile requireProfileForUpdate(Long userId) {
        return profileRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.PROFILE_NOT_FOUND));
    }

    private void invalidateVerificationForBusinessNameChange(
            Long userId,
            String businessName,
            LocalDateTime now
    ) {
        verificationRepository.findByUserIdForUpdate(userId)
                .ifPresent(verification -> verification.invalidateForBusinessProfileChange(businessName, now));
    }

    private MerchantOwnerProfileResponse response(MerchantOwnerProfile profile) {
        List<Long> placeIds = placeRepository.findAllByMerchantOwnerUserIdOrderByPlaceIdAsc(profile.getUserId())
                .stream()
                .map(MerchantOwnerPlace::getPlaceId)
                .toList();
        return MerchantOwnerProfileResponse.from(profile, placeIds);
    }
}
