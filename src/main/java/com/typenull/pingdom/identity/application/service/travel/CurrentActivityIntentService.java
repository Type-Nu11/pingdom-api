package com.typenull.pingdom.identity.application.service.travel;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.repository.UserCurrentActivityIntentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.domain.travel.CurrentActivityIntent;
import com.typenull.pingdom.identity.domain.travel.UserCurrentActivityIntent;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원당 현재 활동 의도 하나를 저장하고 변경 시점부터 2시간의 유효 기간을 부여.
 * 교체·삭제는 회원 행을 잠근 뒤 수행하며 조회에서는 만료된 행을 삭제하지 않고 null로 취급.
 */
@Service
@RequiredArgsConstructor
public class CurrentActivityIntentService {

    private static final Duration INTENT_TTL = Duration.ofHours(2);

    private final UserRepository userRepository;
    private final UserCurrentActivityIntentRepository currentActivityIntentRepository;
    private final Clock clock;

    /**
     * 회원 행을 잠가 기존 활동 의도를 교체하거나 새로 저장하고, 변경 시점부터 2시간 유효한 결과를 반환.
     * 회원이 없으면 USER_NOT_FOUND로 거절하며 기존 의도의 만료 여부와 무관하게 같은 행을 갱신.
     */
    @Transactional
    public UserCurrentActivityIntent replace(Long userId, CurrentActivityIntent intent) {
        User user = findUserForUpdate(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plus(INTENT_TTL);

        return currentActivityIntentRepository.findByUser_Id(userId)
                .map(existing -> {
                    existing.replace(intent, expiresAt);
                    return existing;
                })
                .orElseGet(() -> currentActivityIntentRepository.save(
                        UserCurrentActivityIntent.create(user, intent, expiresAt)
                ));
    }

    @Transactional(readOnly = true)
    public UserCurrentActivityIntent getCurrentIntent(Long userId) {
        findUser(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        return currentActivityIntentRepository.findByUser_Id(userId)
                .filter(activityIntent -> activityIntent.isActiveAt(now))
                .orElse(null);
    }

    @Transactional
    public void clear(Long userId) {
        findUserForUpdate(userId);
        currentActivityIntentRepository.findByUser_Id(userId)
                .ifPresent(currentActivityIntentRepository::delete);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsersException(UsersErrorCode.USER_NOT_FOUND));
    }

    private User findUserForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UsersException(UsersErrorCode.USER_NOT_FOUND));
    }
}
