package com.typenull.pingdom.identity.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.identity.domain.exception.UsersException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ProfileImageFileValidatorTest {

    private final ProfileImageFileValidator validator = new ProfileImageFileValidator();

    /**
     * 실제 PNG 바이트와 image/png 선언이 일치하면 MIME과 확장자 png를 반환하는지 검증한다.
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
     * JPEG 바이트를 image/png로 선언하면 PROFILE_IMAGE_FILE_INVALID인지 검증한다.
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
     * 빈 파일 업로드는 PROFILE_IMAGE_FILE_EMPTY 코드로 거절되는지 검증한다.
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

    /**
     * RGB 단색 JPEG를 생성해 MIME 불일치 검증에 사용할 실제 인코딩 바이트를 제공한다.
     */
    private byte[] jpeg() throws IOException {
        return image("jpg", BufferedImage.TYPE_INT_RGB);
    }

    /**
     * ARGB 단색 PNG를 생성해 유효한 프로필 이미지 입력을 제공한다.
     */
    private byte[] png() throws IOException {
        return image("png", BufferedImage.TYPE_INT_ARGB);
    }

    /**
     * 지정 픽셀 타입의 1×1 이미지를 인코딩하고 ImageIO writer가 없으면 fixture 생성 실패를 명확히 알린다.
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
