package com.typenull.pingdom.identity.application.query;

import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
