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

    /**
     * 프로필 이미지 업로드를 독립적으로 검증하도록 기존 사용자를 정리.
     */
    @BeforeEach
    void setUp() {
        userRepository.deleteAllInBatch();
    }

    /**
     * 업로드 요청으로 생성·수정한 사용자를 제거해 다음 통합 테스트에 데이터가 남지 않게 함.
     */
    @AfterEach
    void tearDown() {
        userRepository.deleteAllInBatch();
    }

    /**
     * 인증 사용자가 JPEG를 올리면 S3 반환 URL이 업로드 응답, 내 정보 응답, 저장 사용자에 동일하게 반영되는지 검증.
     * S3에는 정규화된 파일명과 사용자별 저장 경로가 전달되는지도 확인.
     */
    @Test
    void uploadsAndPersistsProfileImage() throws Exception {
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

    /**
     * JPEG 바이트를 PNG로 선언하면 400과 파일 유효성 오류를 반환하고 S3 업로드를 호출하지 않는지 검증.
     */
    @Test
    void rejectsMismatchedImageContentType() throws Exception {
        User user = saveUser("profileImageInvalid");

        mockMvc.perform(multipart("/users/me/profile-image")
                        .file(new MockMultipartFile("file", "photo.jpg", "image/png", JPEG))
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFILE_IMAGE_FILE_INVALID"));

        verifyNoInteractions(s3ObjectStorage);
    }

    /**
     * S3 연결 오류가 발생하면 프로필 이미지 업로드 응답을 503과 저장소 사용 불가 코드로 변환하는지 검증.
     */
    @Test
    void reportsProfileStorageFailure() throws Exception {
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

    /**
     * 인증 토큰 없이 프로필 이미지를 업로드하면 401을 반환하는지 검증.
     */
    @Test
    void requiresProfileUploadAuthentication() throws Exception {
        mockMvc.perform(multipart("/users/me/profile-image")
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 실제 JWT 발급과 프로필 저장 검증에 사용할 사용자를 저장하고 즉시 flush함.
     */
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

    /**
     * 저장된 사용자의 현재 식별자와 역할로 HTTP 인증에 사용할 Bearer 토큰을 생성.
     */
    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    }

    /**
     * 실제 이미지 형식 검증을 통과하도록 1픽셀 JPEG 바이트를 생성하며 인코더 실패는 테스트 준비 오류로 명시.
     */
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
