package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantVerificationRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantVerificationResponse;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.infrastructure.crypto.MerchantVerificationCipher;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantVerificationService {

    private final MerchantOwnerProfileRepository profileRepository;
    private final MerchantVerificationRepository verificationRepository;
    private final MerchantVerificationCipher verificationCipher;
    private final Clock clock;

    @Transactional
    public MerchantVerificationResponse apply(Long userId, MerchantVerificationRequest request) {
        MerchantOwnerProfile profile = requireProfileForUpdate(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        String encryptedRegistrationNumber = verificationCipher.encrypt(
                request.normalizedBusinessRegistrationNumber()
        );
        MerchantVerification verification = verificationRepository.findByUserIdForUpdate(userId).orElse(null);
        if (verification == null) {
            verification = verificationRepository.save(MerchantVerification.pending(
                    userId,
                    request.normalizedLegalName(),
                    profile.getBusinessName(),
                    encryptedRegistrationNumber,
                    now
            ));
        } else {
            try {
                verification.reapply(
                        request.normalizedLegalName(),
                        profile.getBusinessName(),
                        encryptedRegistrationNumber,
                        now
                );
            } catch (IllegalStateException exception) {
                throw new MerchantOwnerException(MerchantOwnerErrorCode.VERIFICATION_ALREADY_EXISTS);
            }
        }
        return response(verification);
    }

    @Transactional
    public MerchantVerificationResponse update(Long userId, MerchantVerificationRequest request) {
        MerchantOwnerProfile profile = requireProfileForUpdate(userId);
        MerchantVerification verification = requireVerificationForUpdate(userId);
        try {
            verification.update(
                    request.normalizedLegalName(),
                    profile.getBusinessName(),
                    verificationCipher.encrypt(request.normalizedBusinessRegistrationNumber()),
                    LocalDateTime.now(clock)
            );
        } catch (IllegalStateException exception) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.INVALID_VERIFICATION_STATE);
        }
        return response(verification);
    }

    @Transactional(readOnly = true)
    public MerchantVerificationResponse get(Long userId) {
        return response(verificationRepository.findById(userId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.VERIFICATION_NOT_FOUND)));
    }

    private MerchantOwnerProfile requireProfileForUpdate(Long userId) {
        return profileRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.PROFILE_NOT_FOUND));
    }

    private MerchantVerification requireVerificationForUpdate(Long userId) {
        return verificationRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.VERIFICATION_NOT_FOUND));
    }

    private MerchantVerificationResponse response(MerchantVerification verification) {
        return MerchantVerificationResponse.from(
                verification,
                verificationCipher.decrypt(verification.getEncryptedBusinessRegistrationNumber())
        );
    }
}
