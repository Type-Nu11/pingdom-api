package com.typenull.pingdom.domain.auth.service.impl;

import com.typenull.pingdom.domain.auth.domain.User;
import com.typenull.pingdom.domain.auth.dto.LoginRequest;
import com.typenull.pingdom.domain.auth.dto.LoginResponse;
import com.typenull.pingdom.domain.auth.dto.SignupRequest;
import com.typenull.pingdom.domain.auth.dto.UserResponse;
import com.typenull.pingdom.domain.auth.exception.AuthException;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import com.typenull.pingdom.domain.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new AuthException(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다.");
        }

        User user = userRepository.save(User.builder()
                .username(request.username())
                .name(request.name())
                .password(passwordEncoder.encode(request.password()))
                .build());

        return new UserResponse(user.getId(), user.getUsername(), user.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        return new LoginResponse(user.getId(), user.getUsername(), user.getName(), "로그인에 성공했습니다.");
    }
}
