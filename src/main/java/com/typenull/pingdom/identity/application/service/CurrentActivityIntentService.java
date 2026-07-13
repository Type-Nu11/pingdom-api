package com.typenull.pingdom.identity.application.service;

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

@Service
@RequiredArgsConstructor
public class CurrentActivityIntentService {

    private static final Duration INTENT_TTL = Duration.ofHours(2);

    private final UserRepository userRepository;
    private final UserCurrentActivityIntentRepository currentActivityIntentRepository;
    private final Clock clock;

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
