package com.typenull.pingdom.identity.application.service.withdrawal;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.privacy.application.PrivacyProcessingOutboxPublisher;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import com.typenull.pingdom.privacy.event.PrivacyProcessingEvent;
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AuthService facade의 쓰기 트랜잭션에 참여해 탈퇴·익명화와 감사 기록·연관 정리를 조정합니다. */
@Service
@RequiredArgsConstructor
@Transactional
public class UserWithdrawalService {

    private final UserRepository userRepository;
    private final PrivacyProcessingOutboxPublisher privacyProcessingOutboxPublisher;
    private final UserWithdrawalDataService userWithdrawalDataService;
    private final UserAccessStatusService userAccessStatusService;
    private final Clock clock;

    public void withdraw(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (user.isWithdrawn()) {
            return;
        }

        privacyProcessingOutboxPublisher.publish(PrivacyProcessingEvent.userAction(
                user.getId(),
                PrivacyProcessingAction.WITHDRAWAL_REQUESTED,
                "회원 탈퇴 요청"
        ));
        user.withdraw(
                anonymizedUsername(user.getId()),
                anonymizedEmail(user.getId()),
                "WITHDRAWN_" + UUID.randomUUID(),
                now()
        );
        privacyProcessingOutboxPublisher.publish(PrivacyProcessingEvent.userAction(
                user.getId(),
                PrivacyProcessingAction.ANONYMIZED,
                "회원 탈퇴에 따른 개인정보 익명화"
        ));
        userAccessStatusService.evict(user.getId());
        userWithdrawalDataService.cleanupUserOwnedData(user.getId());
    }

    private String anonymizedUsername(Long userId) {
        return "withdrawn_user_" + userId;
    }

    private String anonymizedEmail(Long userId) {
        return "withdrawn_user_%d@withdrawn.local".formatted(userId);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
