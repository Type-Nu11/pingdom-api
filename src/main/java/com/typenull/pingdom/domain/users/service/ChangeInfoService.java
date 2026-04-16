package com.typenull.pingdom.domain.users.service;

import com.typenull.pingdom.domain.auth.domain.User;
import com.typenull.pingdom.domain.auth.exception.AuthErrorCode;
import com.typenull.pingdom.domain.auth.exception.AuthException;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import com.typenull.pingdom.domain.users.dto.ChangePasswordRequest;
import com.typenull.pingdom.domain.users.dto.ChangeUsernameRequest;
import com.typenull.pingdom.domain.users.exception.UsersErrorCode;
import com.typenull.pingdom.domain.users.exception.UsersException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChangeInfoService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsersException(UsersErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public void changeUsername(ChangeUsernameRequest request, Long userId) {
        User user = getUser(userId);

        if(user.getUsername().equals(request.newUsername()) && userRepository.existsByUsername(request.newUsername())){
            throw new UsersException(UsersErrorCode.USERNAME_ALREADY_EXISTS);
        }

        user.changeUsername(request.newUsername());
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, Long userId) {
        User user = getUser(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        request.validatePassword();
        user.changePassword(passwordEncoder.encode(request.newPassword()));
    }
}