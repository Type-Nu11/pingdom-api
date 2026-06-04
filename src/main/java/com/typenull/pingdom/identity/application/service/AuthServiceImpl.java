package com.typenull.pingdom.identity.application.service;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.api.dto.email.EmailVerifyRequest;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.login.LoginResponse;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.api.dto.signup.UserResponse;
import com.typenull.pingdom.identity.api.dto.token.RefreshTokenRequest;
import com.typenull.pingdom.identity.api.dto.token.RefreshTokenResponse;
import com.typenull.pingdom.identity.event.EmailVerificationRequestedEvent;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.application.service.AuthService;
import com.typenull.pingdom.shared.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final long EMAIL_VERIFICATION_EXPIRATION_MINUTES = 10L;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional
    // 이메일 포함 회원가입 저장 기능
    public UserResponse signup(SignupRequest request) {
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
            user.issueEmailVerification(generateVerificationCode(), LocalDateTime.now().plusMinutes(EMAIL_VERIFICATION_EXPIRATION_MINUTES));
        }

        User savedUser = userRepository.save(user);

        if (StringUtils.hasText(savedUser.getEmail()) && StringUtils.hasText(savedUser.getEmailVerificationCode())) {
            // 트랜잭션 커밋 후 인증 메일 발송 이벤트 발행
            applicationEventPublisher.publishEvent(
                    new EmailVerificationRequestedEvent(savedUser.getEmail(), savedUser.getEmailVerificationCode())
            );
        }

        return new UserResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getBirthYear(),
                savedUser.getProfileImageUrl(),
                savedUser.getLanguage(),
                savedUser.getCountry()
        );
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = authenticateUser(request);
        return issueLoginResponse(user);
    }

    @Override
    @Transactional
    public LoginResponse adminLogin(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_CREDENTIALS));

        if (!user.isAdmin()) {
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        if (user.isBanned()) {
            throw new AuthException(AuthErrorCode.USER_BANNED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        return issueLoginResponse(user);
    }

    @Override
    @Transactional
    // 이메일 기준 사용자 인증 처리 메서드
    public void verifyEmail(EmailVerifyRequest request) {
        User user = userRepository.findByEmailAndEmailVerificationCode(request.email(), request.code())
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_EMAIL_VERIFICATION_CODE));

        if (user.isEmailVerificationExpired(LocalDateTime.now())) {
            throw new AuthException(AuthErrorCode.EXPIRED_EMAIL_VERIFICATION_CODE);
        }

        // 이메일 인증 상태 반영 호출
        user.verifyEmail();
    }

    @Override
    @Transactional
    // Refresh Token 기준 토큰 재발급 메서드
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        if (!jwtTokenProvider.validateRefreshToken(request.refreshToken())) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }

        Long userId = jwtTokenProvider.getUserIdFromRefreshToken(request.refreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (!user.matchesRefreshToken(request.refreshToken())) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }

        // 재발급용 Access Token, Refresh Token 생성 호출
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // 새 Refresh Token 회전 반영 호출
        user.issueRefreshToken(refreshToken);

        return new RefreshTokenResponse(accessToken, refreshToken);
    }

    @Override
    @Transactional
    // Refresh Token 무효화 기반 로그아웃 메서드
    public void logout(RefreshTokenRequest request) {
        if (!jwtTokenProvider.validateRefreshToken(request.refreshToken())) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }

        Long userId = jwtTokenProvider.getUserIdFromRefreshToken(request.refreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (!user.matchesRefreshToken(request.refreshToken())) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }

        // 현재 활성 Refresh Token 제거로 재발급 경로 차단
        user.clearRefreshToken();
    }

    // 6자리 이메일 인증 코드 생성 메서드
    private String generateVerificationCode() {
        return "%06d".formatted(ThreadLocalRandom.current().nextInt(1_000_000));
    }

    @Override
    @Transactional
    // 회원탈퇴 하드 딜리트 메서드
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        // 사용자 데이터 완전 삭제 호출
        userRepository.delete(user);
    }

    private User authenticateUser(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_CREDENTIALS));

        if (user.isBanned()) {
            throw new AuthException(AuthErrorCode.USER_BANNED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        return user;
    }

    private LoginResponse issueLoginResponse(User user) {
        // 로그인 성공 시 JWT 발급 호출
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // 현재 활성 Refresh Token 저장 호출
        user.issueRefreshToken(refreshToken);

        return new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getBirthYear(),
                user.getProfileImageUrl(),
                user.getLanguage(),
                user.getCountry(),
                "로그인에 성공했습니다.",
                accessToken,
                refreshToken
        );
    }
}
