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
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3PutResult;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 회원 프로필과 비밀번호를 변경하고 프로필 이미지 저장소 호출을 조정합니다.
 * 이미지는 S3 업로드 후 DB에 URL을 반영하므로 두 저장소 사이의 원자적 커밋은 제공하지 않습니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChangeInfoService {

    private static final String PROFILE_IMAGE_PREFIX = "users/profile-images";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProfileImageFileValidator profileImageFileValidator;
    private final S3ObjectStorage s3ObjectStorage;

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsersException(UsersErrorCode.USER_NOT_FOUND));
    }

    /**
     * 회원을 찾아 요청 이름으로 변경합니다. 회원이 없으면 USER_NOT_FOUND를 반환합니다.
     * 현재 이름과 요청 이름이 같고 해당 이름이 이미 존재하면 USERNAME_ALREADY_EXISTS로 거절합니다.
     */
    @Transactional
    public void changeUsername(ChangeUsernameRequest request, Long userId) {
        User user = getUser(userId);

        if(user.getUsername().equals(request.newUsername()) && userRepository.existsByUsername(request.newUsername())){
            throw new UsersException(UsersErrorCode.USERNAME_ALREADY_EXISTS);
        }

        user.changeUsername(request.newUsername());
    }

    /**
     * 현재 비밀번호가 일치하는 회원에 대해 새 비밀번호와 확인값의 일치를 검증한 뒤 암호화해 교체합니다.
     * 회원 부재나 자격 증명 불일치는 거절하며, 이 경로에서는 기존 refresh token을 변경하지 않습니다.
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
     * 새 이미지를 업로드한 뒤 URL을 flush하며, 여기서 관찰한 DB 실패에는 업로드 객체 삭제를 시도합니다.
     * 삭제 실패는 원래 예외를 대체하지 않습니다. 메서드 반환 뒤 커밋 실패와 이전 이미지 정리는 이 보상 범위에 포함되지 않습니다.
     */
    @Transactional
    public String changeProfileImage(MultipartFile file, Long userId) {
        User user = getUser(userId);
        ProfileImageFileValidator.ValidatedProfileImage validated = profileImageFileValidator.validate(file);
        S3PutResult uploaded = upload(validated, userId);

        try {
            user.changeProfileImageUrl(uploaded.url());
            userRepository.saveAndFlush(user);
            return uploaded.url();
        } catch (RuntimeException exception) {
            cleanupUploadedObject(uploaded.key());
            throw exception;
        }
    }

    private S3PutResult upload(ProfileImageFileValidator.ValidatedProfileImage image, Long userId) {
        try {
            return s3ObjectStorage.put(
                    image.bytes(),
                    "profile." + image.extension(),
                    image.contentType(),
                    PROFILE_IMAGE_PREFIX + "/" + userId
            );
        } catch (S3StorageException exception) {
            throw new UsersException(UsersErrorCode.PROFILE_IMAGE_STORAGE_UNAVAILABLE);
        }
    }

    private void cleanupUploadedObject(String key) {
        try {
            s3ObjectStorage.delete(key);
        } catch (RuntimeException exception) {
            log.warn("프로필 이미지 DB 저장 실패 후 S3 객체 정리에 실패했습니다. key={}", key, exception);
        }
    }
}
