package com.typenull.pingdom.moderation.application.service;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.api.dto.ban.BanResponse;
import com.typenull.pingdom.moderation.api.dto.user.AdminBannedUserItem;
import com.typenull.pingdom.moderation.api.dto.user.AdminBannedUserResponse;
import com.typenull.pingdom.moderation.application.AdminUserService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public BanResponse banUser(Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        user.ban(reason, now);

        return new BanResponse(user.getId(), user.isBanned(), user.getBannedAt(), user.getBanReason());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminBannedUserResponse listBannedUsers() {
        List<AdminBannedUserItem> users = userRepository.findAllByBannedTrueOrderByBannedAtDescIdDesc().stream()
                .map(this::toItem)
                .toList();

        return AdminBannedUserResponse.of(users);
    }

    private AdminBannedUserItem toItem(User user) {
        return new AdminBannedUserItem(
                user.getId(),
                user.getUsername(),
                user.isBanned()
        );
    }
}
