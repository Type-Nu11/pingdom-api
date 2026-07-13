package com.typenull.pingdom.identity.application.service;

import com.typenull.pingdom.identity.domain.TravelPurpose;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TravelPurposePreferenceService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Set<TravelPurpose> getTravelPurposes(Long userId) {
        return findUser(userId).currentTravelPurposes();
    }

    @Transactional
    public Set<TravelPurpose> replaceTravelPurposes(Long userId, Set<TravelPurpose> travelPurposes) {
        User user = findUser(userId);
        user.replaceTravelPurposes(travelPurposes);
        return user.currentTravelPurposes();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsersException(UsersErrorCode.USER_NOT_FOUND));
    }
}
