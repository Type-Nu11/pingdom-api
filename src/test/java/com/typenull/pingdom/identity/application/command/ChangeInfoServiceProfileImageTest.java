package com.typenull.pingdom.identity.application.command;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.*;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class ChangeInfoServiceProfileImageTest {
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ProfileImageFileValidator profileImageFileValidator;
    @Mock private S3ObjectStorage storage;
    @Mock private ProfileImageUploadService profileImages;
    @Mock private ProfileImageStorageProperties properties;
    @InjectMocks private ChangeInfoService service;

    @Test
    void registersBeforeUploadAndCompletesWithoutUnconditionalDelete() {
        User user = prepare();
        when(profileImages.complete("intent", user)).thenReturn("https://example.com/new.jpg");
        assertThat(service.changeProfileImage(file(), 1L)).isEqualTo("https://example.com/new.jpg");
        var order = inOrder(profileImages, storage);
        order.verify(profileImages).prepare(1L, "jpg");
        order.verify(storage).putAtKey(any(byte[].class), eq("image/jpeg"), eq("owned-key"), any());
        order.verify(profileImages).complete("intent", user);
        verify(storage, never()).delete(anyString());
    }

    @Test
    void optimisticFailureKeepsDurableIntentInsteadOfDeletingPossiblyCommittedObject() {
        User user = prepare();
        var failure = new ObjectOptimisticLockingFailureException(User.class, 1L);
        when(profileImages.complete("intent", user)).thenThrow(failure);
        assertThatThrownBy(() -> service.changeProfileImage(file(), 1L)).isSameAs(failure);
        verify(storage, never()).delete(anyString());
    }

    @Test
    void partialUploadFailurePreservesIntentAndOriginalStorageResponse() {
        prepare();
        doThrow(new S3ObjectStorage.S3StorageException(S3ObjectStorage.S3StorageError.CONNECTION_ERROR, "unavailable", null))
                .when(storage).putAtKey(any(byte[].class), anyString(), anyString(), any());
        assertThatThrownBy(() -> service.changeProfileImage(file(), 1L)).isInstanceOfSatisfying(UsersException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(UsersErrorCode.PROFILE_IMAGE_STORAGE_UNAVAILABLE));
        verify(profileImages, never()).complete(anyString(), any());
        verify(storage, never()).delete(anyString());
    }

    private User prepare() {
        User user = User.builder().id(1L).profileImageUrl("https://external.example/current.jpg").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(profileImageFileValidator.validate(any())).thenReturn(
                new ProfileImageFileValidator.ValidatedProfileImage(new byte[]{1}, "image/jpeg", "jpg"));
        when(profileImages.prepare(1L, "jpg")).thenReturn(new ProfileImageUploadService.PreparedUpload("intent", "owned-key"));
        when(properties.uploadTimeout()).thenReturn(Duration.ofMinutes(2));
        return user;
    }

    private MockMultipartFile file() { return new MockMultipartFile("file", "profile.jpg", "image/jpeg", new byte[]{1}); }
}
