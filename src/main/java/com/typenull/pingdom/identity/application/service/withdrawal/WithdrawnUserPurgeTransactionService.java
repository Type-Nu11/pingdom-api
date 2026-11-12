package com.typenull.pingdom.identity.application.service.withdrawal;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserPurgeRetry;
import com.typenull.pingdom.identity.domain.UserPurgeRetry.Reason;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.repository.OAuthAccountRepository;
import com.typenull.pingdom.identity.domain.repository.PasswordResetTokenRepository;
import com.typenull.pingdom.identity.domain.repository.UserPurgeRetryRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.infrastructure.persistence.UserPurgeReferenceRepository;
import com.typenull.pingdom.privacy.application.PrivacyProcessingOutboxPublisher;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import com.typenull.pingdom.privacy.event.PrivacyProcessingBulkEvent;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 한 사용자 삭제와 감사 발행만 원자적으로 처리해 다른 사용자의 실패와 격리한다. */
@Service
@RequiredArgsConstructor
public class WithdrawnUserPurgeTransactionService {

    public enum Result { DELETED, DEFERRED, SKIPPED }

    private final UserRepository userRepository;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserPurgeRetryRepository retryRepository;
    private final UserPurgeReferenceRepository referenceRepository;
    private final UserWithdrawalDataService withdrawalDataService;
    private final UserWithdrawalProperties properties;
    private final PrivacyProcessingOutboxPublisher privacyProcessingOutboxPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result purge(Long userId, LocalDateTime cutoff, LocalDateTime now) {
        if (!referenceRepository.tryAcquireLock()) {
            return Result.SKIPPED;
        }
        User user = userRepository.findByIdForPurge(userId).orElse(null);
        if (!isExpired(user, cutoff)) {
            return Result.SKIPPED;
        }
        if (retryRepository.findById(userId).filter(retry -> retry.getNextAttemptAt().isAfter(now)).isPresent()) {
            return Result.SKIPPED;
        }
        if (referenceRepository.hasRetainedReferences(userId)) {
            defer(userId, Reason.RETAINED_REFERENCE, now);
            return Result.DEFERRED;
        }

        List<Long> ids = List.of(userId);
        withdrawalDataService.detachContentUserReferences(ids);
        passwordResetTokenRepository.deleteAllByUserIds(ids);
        oAuthAccountRepository.deleteAllByUserIds(ids);
        retryRepository.deleteById(userId);
        userRepository.delete(user);
        userRepository.flush();
        privacyProcessingOutboxPublisher.publish(PrivacyProcessingBulkEvent.systemAction(
                ids, PrivacyProcessingAction.DELETED, "보존기간 만료에 따른 탈퇴 사용자 최종 삭제"
        ));
        return Result.DELETED;
    }

    /** 실패 트랜잭션이 끝난 뒤에만 호출해 재시도 상태가 함께 롤백되지 않게 한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(Long userId, LocalDateTime cutoff, LocalDateTime now) {
        if (!referenceRepository.tryAcquireLock()) {
            return false;
        }
        User user = userRepository.findByIdForPurge(userId).orElse(null);
        if (!isExpired(user, cutoff)) {
            return false;
        }
        defer(userId, Reason.PROCESSING_FAILED, now);
        return true;
    }

    private boolean isExpired(User user, LocalDateTime cutoff) {
        return user != null && user.getStatus() == UserStatus.WITHDRAWN
                && user.getWithdrawnAt() != null && !user.getWithdrawnAt().isAfter(cutoff);
    }

    private void defer(Long userId, Reason reason, LocalDateTime now) {
        retryRepository.save(UserPurgeRetry.defer(userId, reason, now, now.plus(properties.cleanupDelay())));
    }
}
