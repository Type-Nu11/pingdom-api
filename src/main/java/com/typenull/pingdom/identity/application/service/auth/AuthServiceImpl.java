package com.typenull.pingdom.identity.application.service.auth;

import com.typenull.pingdom.identity.api.dto.email.EmailResendRequest;
import com.typenull.pingdom.identity.api.dto.email.EmailVerifyRequest;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.passwordreset.PasswordResetConfirmRequest;
import com.typenull.pingdom.identity.api.dto.passwordreset.PasswordResetRequest;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.api.dto.signup.UserResponse;
import com.typenull.pingdom.identity.application.service.withdrawal.UserWithdrawalService;
import com.typenull.pingdom.identity.domain.ProfileImageUploadIntent;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 가입과 공개 인증 진입점의 트랜잭션을 소유하고 세션·인증·복구·탈퇴의 실행을 위임합니다. */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final AuthSessionService authSessionService;
    private final UserWithdrawalService userWithdrawalService;

    @Override
    @Transactional
    public UserResponse signup(SignupRequest request) {
        // tracked key는 업로드 intent 확정으로만 연결해야 정리 직전 조회 이후의 새 참조 경합도 막을 수 있습니다.
        if (ProfileImageUploadIntent.isReservedProfileUrl(request.profileImageUrl())) {
            throw new AuthException(AuthErrorCode.PROFILE_IMAGE_URL_RESERVED);
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new AuthException(AuthErrorCode.DUPLICATE_USERNAME);
        }
        if (StringUtils.hasText(request.email()) && userRepository.existsByEmail(request.email())) {
            throw new AuthException(AuthErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .birthYear(request.birthYear())
                .profileImageUrl(request.profileImageUrl())
                .language(request.language())
                .country(request.country())
                .build();

        if (StringUtils.hasText(request.email())) {
            // 이메일 인증 코드 발급 처리
            emailVerificationService.issueEmailVerification(user);
        }

        User savedUser = userRepository.save(user);

        emailVerificationService.storeEmailVerificationOutboxEvent(savedUser);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getBirthYear(),
                savedUser.getProfileImageUrl(),
                savedUser.getLanguage(),
                savedUser.getCountry(),
                savedUser.getRole()
        );
    }

    @Override
    @Transactional
    public LoginResult login(LoginRequest request) {
        return authSessionService.login(request);
    }

    @Override
    @Transactional
    public LoginResult adminLogin(LoginRequest request) {
        return authSessionService.adminLogin(request);
    }

    @Override
    @Transactional
    public void resendVerificationEmail(EmailResendRequest request) {
        emailVerificationService.resendVerificationEmail(request);
    }

    @Override
    @Transactional
    public void verifyEmail(EmailVerifyRequest request) {
        emailVerificationService.verifyEmail(request);
    }

    @Override
    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        passwordResetService.requestPasswordReset(request);
    }

    @Override
    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        passwordResetService.confirmPasswordReset(request);
    }

    @Override
    @Transactional
    public TokenRefreshResult refreshToken(String refreshToken) {
        return authSessionService.refreshToken(refreshToken);
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        authSessionService.logout(refreshToken);
    }

    @Override
    @Transactional
    public void withdraw(Long userId) {
        userWithdrawalService.withdraw(userId);
    }
}
