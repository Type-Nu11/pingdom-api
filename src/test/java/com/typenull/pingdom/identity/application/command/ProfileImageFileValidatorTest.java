package com.typenull.pingdom.identity.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.identity.domain.exception.UsersException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

class ProfileImageFileValidatorTest {

    private final ProfileImageFileValidator validator = new ProfileImageFileValidator();

    /**
     * 실제 PNG 바이트와 image/png 선언이 일치하면 MIME과 확장자 png를 반환하는지 검증.
     */
    @Test
    void acceptsMatchingProfileImageType() throws IOException {
        ProfileImageFileValidator.ValidatedProfileImage image = validator.validate(
                new MockMultipartFile("file", "profile.png", "image/png", png())
        );

        assertThat(image.contentType()).isEqualTo("image/png");
        assertThat(image.extension()).isEqualTo("png");
    }

    /**
     * JPEG 바이트를 image/png로 선언하면 PROFILE_IMAGE_FILE_INVALID인지 검증.
     */
    @Test
    void rejectsMismatchedProfileImageType() throws IOException {
        assertThatThrownBy(() -> validator.validate(
                new MockMultipartFile("file", "profile.jpg", "image/png", jpeg())
        ))
                .isInstanceOf(UsersException.class)
                .extracting(exception -> ((UsersException) exception).getErrorCode().getCode())
                .isEqualTo("PROFILE_IMAGE_FILE_INVALID");
    }

    /**
     * 빈 파일 업로드는 PROFILE_IMAGE_FILE_EMPTY 코드로 거절되는지 검증.
     */
    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> validator.validate(
                new MockMultipartFile("file", "profile.jpg", "image/jpeg", new byte[0])
        ))
                .isInstanceOf(UsersException.class)
                .extracting(exception -> ((UsersException) exception).getErrorCode().getCode())
                .isEqualTo("PROFILE_IMAGE_FILE_EMPTY");
    }

    /** 커뮤니티에도 쓰는 검증기가 10MiB 초과 및 정확히 20MiB인 JPEG를 정규화해 받는지 확인. */
    @ParameterizedTest
    @ValueSource(ints = {10_485_761, 20_971_520})
    void acceptsCameraJpegUpToTwentyMiB(int size) throws IOException {
        byte[] content = Arrays.copyOf(jpeg(), size);

        var result = validator.validate(new MockMultipartFile("file", "camera.jpg", "image/jpeg", content));

        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.bytes()).isNotEmpty().hasSizeLessThan(size);
    }

    @Test
    void rejectsJpegAboveTwentyMiB() {
        assertThatThrownBy(() -> validator.validate(new MockMultipartFile(
                "file", "camera.jpg", "image/jpeg", new byte[20_971_521])))
                .isInstanceOf(UsersException.class)
                .extracting(exception -> ((UsersException) exception).getErrorCode().getCode())
                .isEqualTo("PROFILE_IMAGE_FILE_TOO_LARGE");
    }

    /**
     * RGB 단색 JPEG를 생성해 MIME 불일치 검증에 사용할 실제 인코딩 바이트를 제공.
     */
    private byte[] jpeg() throws IOException {
        return image("jpg", BufferedImage.TYPE_INT_RGB);
    }

    /**
     * ARGB 단색 PNG를 생성해 유효한 프로필 이미지 입력을 제공.
     */
    private byte[] png() throws IOException {
        return image("png", BufferedImage.TYPE_INT_ARGB);
    }

    /**
     * 지정 픽셀 타입의 1×1 이미지를 인코딩하고 ImageIO writer가 없으면 fixture 생성 실패를 명확히 알림.
     */
    private byte[] image(String format, int imageType) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(new BufferedImage(1, 1, imageType), format, output)) {
                throw new IllegalStateException(format + " writer is not available.");
            }
            return output.toByteArray();
        }
    }
}
