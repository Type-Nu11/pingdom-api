package com.typenull.pingdom.identity.application.query;

import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 기본 프로필을 읽어 API 표현과 분리된 조회 결과로 반환합니다.
 * 존재하지 않는 ID는 빈 응답 대신 USER_NOT_FOUND로 처리합니다.
 */
@Service
@RequiredArgsConstructor
public class MyPageService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MyPageQueryResult getMyPageInfo(Long userId) {
        return userRepository.findById(userId)
                .map(MyPageQueryResult::from)
                .orElseThrow(() -> new UsersException(UsersErrorCode.USER_NOT_FOUND));
    }
}
