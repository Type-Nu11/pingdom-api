package com.typenull.pingdom.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3PutResult;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageError;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class ProfileImageControllerTest {

    private static final byte[] JPEG = validJpeg();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private S3ObjectStorage s3ObjectStorage;

    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAllInBatch();
    }

    @Test
    void uploadsProfileImageAndUpdatesMyPageProfileImageUrl() throws Exception {
        User user = saveUser("profileImageOwner");
        String imageUrl = "https://bucket.s3.ap-northeast-2.amazonaws.com/users/profile-images/1/profile.jpg";
        when(s3ObjectStorage.put(any(byte[].class), eq("profile.jpg"), eq("image/jpeg"),
                eq("users/profile-images/" + user.getId())))
                .thenReturn(new S3PutResult("users/profile-images/1/profile.jpg", imageUrl));

        mockMvc.perform(multipart("/users/me/profile-image")
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value(imageUrl));

        mockMvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value(imageUrl));

        assertThat(userRepository.findById(user.getId()))
                .get()
                .extracting(User::getProfileImageUrl)
                .isEqualTo(imageUrl);
        verify(s3ObjectStorage).put(any(byte[].class), eq("profile.jpg"), eq("image/jpeg"),
                eq("users/profile-images/" + user.getId()));
    }

    @Test
    void rejectsMismatchedImageContentTypeBeforeUploading() throws Exception {
        User user = saveUser("profileImageInvalid");

        mockMvc.perform(multipart("/users/me/profile-image")
                        .file(new MockMultipartFile("file", "photo.jpg", "image/png", JPEG))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFILE_IMAGE_FILE_INVALID"));

        verifyNoInteractions(s3ObjectStorage);
    }

    @Test
    void returnsStorageUnavailableWhenS3UploadFails() throws Exception {
        User user = saveUser("profileImageStorageFailure");
        when(s3ObjectStorage.put(any(byte[].class), eq("profile.jpg"), eq("image/jpeg"),
                eq("users/profile-images/" + user.getId())))
                .thenThrow(new S3StorageException(S3StorageError.CONNECTION_ERROR, "connection failed", null));

        mockMvc.perform(multipart("/users/me/profile-image")
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROFILE_IMAGE_STORAGE_UNAVAILABLE"));
    }

    @Test
    void profileImageUploadRequiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/users/me/profile-image")
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG)))
                .andExpect(status().isUnauthorized());
    }

    private User saveUser(String username) {
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("password")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .build());
    }

    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    }

    private static byte[] validJpeg() {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            if (!ImageIO.write(image, "jpg", output)) {
                throw new IllegalStateException("JPEG writer is not available.");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create test JPEG.", exception);
        }
    }
}
