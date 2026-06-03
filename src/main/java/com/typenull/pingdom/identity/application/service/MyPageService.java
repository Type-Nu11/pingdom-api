package com.typenull.pingdom.identity.application.service;

import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.JwtTokenProvider;
import com.typenull.pingdom.identity.api.dto.profile.MyPageResponse;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyPageService { // 마이페이지는 하나로 될 것 같아서 impl추가 안했습니다.

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    //마이페이지 정보 반환 메서드
    public MyPageResponse getMyPageInfo(Long userId){

        return userRepository.findById(userId)
                .map(MyPageResponse::from)
                .orElseThrow(() -> new UsersException(UsersErrorCode.USER_NOT_FOUND));
    }
}
