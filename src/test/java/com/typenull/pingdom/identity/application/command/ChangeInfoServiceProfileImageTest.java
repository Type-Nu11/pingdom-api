package com.typenull.pingdom.identity.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3PutResult;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class ChangeInfoServiceProfileImageTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ProfileImageFileValidator profileImageFileValidator;

    @Mock
    private S3ObjectStorage s3ObjectStorage;

    @InjectMocks
    private ChangeInfoService changeInfoService;

    @Test
    void deletesUploadedObjectWhenProfileImagePersistenceFails() {
        User user = user();
        MockMultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg", new byte[]{1});
        ProfileImageFileValidator.ValidatedProfileImage image =
                new ProfileImageFileValidator.ValidatedProfileImage(new byte[]{1}, "image/jpeg", "jpg");
        S3PutResult uploaded = new S3PutResult("users/profile-images/1/profile.jpg", "https://example.com/profile.jpg");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(profileImageFileValidator.validate(file)).thenReturn(image);
        when(s3ObjectStorage.put(any(byte[].class), eq("profile.jpg"), eq("image/jpeg"),
                eq("users/profile-images/" + user.getId()))).thenReturn(uploaded);
        when(userRepository.saveAndFlush(user)).thenThrow(new DataIntegrityViolationException("save failed"));

        assertThatThrownBy(() -> changeInfoService.changeProfileImage(file, user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(s3ObjectStorage).delete(uploaded.key());
    }

    @Test
    void persistsUploadedProfileImageUrl() {
        User user = user();
        MockMultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg", new byte[]{1});
        ProfileImageFileValidator.ValidatedProfileImage image =
                new ProfileImageFileValidator.ValidatedProfileImage(new byte[]{1}, "image/jpeg", "jpg");
        S3PutResult uploaded = new S3PutResult("users/profile-images/1/profile.jpg", "https://example.com/profile.jpg");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(profileImageFileValidator.validate(file)).thenReturn(image);
        when(s3ObjectStorage.put(any(byte[].class), eq("profile.jpg"), eq("image/jpeg"),
                eq("users/profile-images/" + user.getId()))).thenReturn(uploaded);
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        String profileImageUrl = changeInfoService.changeProfileImage(file, user.getId());

        assertThat(profileImageUrl).isEqualTo(uploaded.url());
        assertThat(user.getProfileImageUrl()).isEqualTo(uploaded.url());
        verify(userRepository).saveAndFlush(user);
    }

    private User user() {
        return User.builder()
                .id(1L)
                .username("profileImageOwner")
                .email("profile-image-owner@example.com")
                .password("password")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .build();
    }
}
