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
