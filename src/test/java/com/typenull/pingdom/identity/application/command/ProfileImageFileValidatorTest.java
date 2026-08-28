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

    @Test
    void acceptsImageWhenSignatureAndContentTypeMatch() throws IOException {
        ProfileImageFileValidator.ValidatedProfileImage image = validator.validate(
                new MockMultipartFile("file", "profile.png", "image/png", png())
        );

        assertThat(image.contentType()).isEqualTo("image/png");
        assertThat(image.extension()).isEqualTo("png");
    }

    @Test
    void rejectsImageWhenSignatureAndContentTypeDoNotMatch() throws IOException {
        assertThatThrownBy(() -> validator.validate(
                new MockMultipartFile("file", "profile.jpg", "image/png", jpeg())
        ))
                .isInstanceOf(UsersException.class)
                .extracting(exception -> ((UsersException) exception).getErrorCode().getCode())
                .isEqualTo("PROFILE_IMAGE_FILE_INVALID");
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> validator.validate(
                new MockMultipartFile("file", "profile.jpg", "image/jpeg", new byte[0])
        ))
                .isInstanceOf(UsersException.class)
                .extracting(exception -> ((UsersException) exception).getErrorCode().getCode())
                .isEqualTo("PROFILE_IMAGE_FILE_EMPTY");
    }

    private byte[] jpeg() throws IOException {
        return image("jpg", BufferedImage.TYPE_INT_RGB);
    }

    private byte[] png() throws IOException {
        return image("png", BufferedImage.TYPE_INT_ARGB);
    }

    private byte[] image(String format, int imageType) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(new BufferedImage(1, 1, imageType), format, output)) {
                throw new IllegalStateException(format + " writer is not available.");
            }
            return output.toByteArray();
        }
    }
}
