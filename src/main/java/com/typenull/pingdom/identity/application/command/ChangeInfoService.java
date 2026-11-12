package com.typenull.pingdom.identity.application.command;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.api.dto.profile.ChangePasswordRequest;
import com.typenull.pingdom.identity.api.dto.profile.ChangeUsernameRequest;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageException;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 회원 프로필과 비밀번호를 변경하고 프로필 이미지 저장소 호출을 조정.
 * 이미지는 S3 업로드 후 DB에 URL을 반영하므로 두 저장소 사이의 원자적 커밋은 보장 범위에서 제외.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChangeInfoService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProfileImageFileValidator profileImageFileValidator;
    private final S3ObjectStorage s3ObjectStorage;
    private final ProfileImageUploadService profileImages;
    private final ProfileImageStorageProperties profileImageStorageProperties;

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsersException(UsersErrorCode.USER_NOT_FOUND));
    }

    /**
     * 회원을 찾아 요청 이름으로 변경. 회원이 없으면 USER_NOT_FOUND를 반환.
     * 현재 이름 유지는 허용하고 다른 회원의 이름 또는 DB 유일 제약 경쟁은 같은 중복 오류로 거절.
     */
    @Transactional
    public void changeUsername(ChangeUsernameRequest request, Long userId) {
        User user = getUser(userId);

        if (user.getUsername().equals(request.newUsername())) {
            return;
        }
        if (userRepository.existsByUsername(request.newUsername())) {
            throw new UsersException(UsersErrorCode.USERNAME_ALREADY_EXISTS);
        }

        user.changeUsername(request.newUsername());
        try {
            // 커밋 전에 경쟁 실패를 관찰해야 변경 API의 기존 도메인 오류 계약을 보존할 수 있습니다.
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            if (isUsernameUniqueViolation(exception)) {
                throw new UsersException(UsersErrorCode.USERNAME_ALREADY_EXISTS);
            }
            throw exception;
        }
    }

    private boolean isUsernameUniqueViolation(Throwable exception) {
        Set<Throwable> visited = new HashSet<>();
        for (Throwable cause = exception; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && "uk_users_username".equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 현재 비밀번호가 일치하는 회원에 대해 새 비밀번호와 확인값의 일치를 검증한 뒤 암호화해 교체.
     * 회원 부재나 자격 증명 불일치는 거절하며 이 경로에서 기존 refresh token은 유지.
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request, Long userId) {
        User user = getUser(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        request.validatePassword();
        user.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    /**
     * 별도 커밋한 소유 key에 업로드하고 User version 검사와 원장 연결을 기존 수정 TX로 확정.
     * flush/실제 commit 실패 시 선등록 원장이 남아 14일 유예 후 현재 참조를 재검증하여 정리합니다.
     */
    @Transactional
    public String changeProfileImage(MultipartFile file, Long userId) {
        User user = getUser(userId);
        ProfileImageFileValidator.ValidatedProfileImage validated = profileImageFileValidator.validate(file);
        try {
            var prepared = profileImages.prepare(userId, validated.extension());
            s3ObjectStorage.putAtKey(validated.bytes(), validated.contentType(), prepared.key(),
                    profileImageStorageProperties.uploadTimeout());
            return profileImages.complete(prepared.intentId(), user);
        } catch (S3StorageException exception) {
            throw new UsersException(UsersErrorCode.PROFILE_IMAGE_STORAGE_UNAVAILABLE);
        }
    }
}
