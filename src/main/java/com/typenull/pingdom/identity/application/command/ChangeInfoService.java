package com.typenull.pingdom.identity.application.command;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.api.dto.profile.ChangePasswordRequest;
import com.typenull.pingdom.identity.api.dto.profile.ChangeUsernameRequest;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
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
