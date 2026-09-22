package com.typenull.pingdom.identity.application.service.travel;

import com.typenull.pingdom.identity.domain.TravelPurpose;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원의 장기 여행 목적 집합을 조회하거나 요청한 집합으로 전체 교체.
 * null 집합을 빈 선호로 바꾸고 읽기 전용 복사본을 만드는 규칙은 User에 위임.
 */
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
