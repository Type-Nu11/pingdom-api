package com.typenull.pingdom.post.infrastructure.storage.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ImageUploadProcessorTest {

    private final ImageUploadProcessor processor = new ImageUploadProcessor();

    @Test
    void processReencodesImageAndCreatesBoundedThumbnail() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "post.jpg",
                "image/jpeg",
                imageBytes("jpg", 1_024, 768)
        );

        ProcessedImageUpload result = processor.process(file);

        assertEquals("image/jpeg", result.contentType());
        assertEquals("post-processed.jpg", result.originalFilename());
        assertEquals("post-thumbnail.jpg", result.thumbnailFilename());
        assertTrue(result.originalBytes().length > 0);
        assertTrue(result.thumbnailBytes().length > 0);
        assertEquals(1_024, result.width());
        assertEquals(768, result.height());
        assertEquals(512, result.thumbnailWidth());
        assertEquals(384, result.thumbnailHeight());
    }

    @Test
    void processRejectsUnsupportedMagicBytes() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "post.jpg",
                "image/jpeg",
                "not-image".getBytes()
        );

        MapException exception = assertThrows(MapException.class, () -> processor.process(file));

        assertEquals(MapErrorCode.UNSUPPORTED_IMAGE_TYPE, exception.getErrorCode());
    }

    @Test
    void processRejectsContentTypeMismatch() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "post.jpg",
                "image/png",
                imageBytes("jpg", 10, 10)
        );

        MapException exception = assertThrows(MapException.class, () -> processor.process(file));

        assertEquals(MapErrorCode.INVALID_IMAGE_FILE, exception.getErrorCode());
    }

    @Test
    void processRejectsBrokenImagePayloadWithValidMagicBytes() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "broken.jpg",
                "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01}
        );

        MapException exception = assertThrows(MapException.class, () -> processor.process(file));

        assertEquals(MapErrorCode.INVALID_IMAGE_FILE, exception.getErrorCode());
    }

    private byte[] imageBytes(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, format, outputStream);
        return outputStream.toByteArray();
    }
}
