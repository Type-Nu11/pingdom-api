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

    /**
     * 1,024×768 JPEG를 처리하면 원본 크기·JPEG 타입·파생 파일명과 512×384 썸네일 및 비어 있지 않은 바이트를 반환하는지 검증.
     */
    @Test
    void reencodesImageAndBoundsThumbnail() throws Exception {
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

    /**
     * 이름·MIME만 JPEG인 비이미지 payload는 UNSUPPORTED_IMAGE_TYPE으로 거절되는지 검증.
     */
    @Test
    void rejectsUnsupportedMagicBytes() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "post.jpg",
                "image/jpeg",
                "not-image".getBytes()
        );

        MapException exception = assertThrows(MapException.class, () -> processor.process(file));

        assertEquals(MapErrorCode.UNSUPPORTED_IMAGE_TYPE, exception.getErrorCode());
    }

    /**
     * JPEG 바이트를 image/png로 선언하면 INVALID_IMAGE_FILE인지 검증.
     */
    @Test
    void rejectsImageContentTypeMismatch() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "post.jpg",
                "image/png",
                imageBytes("jpg", 10, 10)
        );

        MapException exception = assertThrows(MapException.class, () -> processor.process(file));

        assertEquals(MapErrorCode.INVALID_IMAGE_FILE, exception.getErrorCode());
    }

    /**
     * JPEG magic byte만 갖춘 손상 payload는 INVALID_IMAGE_FILE인지 검증해 헤더 일치만으로 업로드를 허용하지 않도록 함.
     */
    @Test
    void rejectsBrokenImagePayload() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "broken.jpg",
                "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01}
        );

        MapException exception = assertThrows(MapException.class, () -> processor.process(file));

        assertEquals(MapErrorCode.INVALID_IMAGE_FILE, exception.getErrorCode());
    }

    /**
     * 너비 8,001인 JPEG가 IMAGE_RESOLUTION_TOO_LARGE로 거절되는지 검증. 디코더 호출 순서는 직접 검증 대상에서 제외.
     */
    @Test
    void rejectsExcessiveImageResolution() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "huge.jpg", "image/jpeg", imageBytes("jpg", 8_001, 1)
        );

        MapException exception = assertThrows(MapException.class, () -> processor.process(file));

        assertEquals(MapErrorCode.IMAGE_RESOLUTION_TOO_LARGE, exception.getErrorCode());
    }

    /**
     * 지정 형식·크기의 RGB 이미지를 ImageIO로 인코딩해 타입 일치와 해상도 경계의 실제 바이트 입력을 제공.
     */
    private byte[] imageBytes(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, format, outputStream);
        return outputStream.toByteArray();
    }
}
