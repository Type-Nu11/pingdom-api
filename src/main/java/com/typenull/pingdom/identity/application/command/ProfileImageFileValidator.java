package com.typenull.pingdom.identity.application.command;

import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/** 프로필 이미지의 크기·실제 형식·해상도를 검증하고 메타데이터를 제거해 저장합니다. */
@Component
public class ProfileImageFileValidator {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_WIDTH = 8_000;
    private static final int MAX_HEIGHT = 8_000;
    private static final long MAX_PIXEL_COUNT = 36_000_000L;

    public ValidatedProfileImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UsersException(UsersErrorCode.PROFILE_IMAGE_FILE_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new UsersException(UsersErrorCode.PROFILE_IMAGE_FILE_TOO_LARGE);
        }

        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0) {
                throw new UsersException(UsersErrorCode.PROFILE_IMAGE_FILE_EMPTY);
            }
            if (bytes.length > MAX_FILE_SIZE_BYTES) {
                throw new UsersException(UsersErrorCode.PROFILE_IMAGE_FILE_TOO_LARGE);
            }

            ImageType imageType = ImageType.detect(bytes);
            if (imageType == null || !imageType.contentType.equals(normalizeContentType(file.getContentType()))) {
                throw new UsersException(UsersErrorCode.PROFILE_IMAGE_FILE_INVALID);
            }
            byte[] normalizedBytes = decodeAndReencode(bytes, imageType);
            if (normalizedBytes.length > MAX_FILE_SIZE_BYTES) {
                throw new UsersException(UsersErrorCode.PROFILE_IMAGE_FILE_TOO_LARGE);
            }
            return new ValidatedProfileImage(normalizedBytes, imageType.contentType, imageType.extension);
        } catch (IOException exception) {
            throw new UsersException(UsersErrorCode.PROFILE_IMAGE_FILE_INVALID);
        }
    }

    private String normalizeContentType(String contentType) {
        return StringUtils.hasText(contentType) ? contentType.trim().toLowerCase(Locale.ROOT) : "";
    }

    private byte[] decodeAndReencode(byte[] bytes, ImageType imageType) throws IOException {
        ImageIO.setUseCache(false);
        BufferedImage decoded;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new UsersException(UsersErrorCode.PROFILE_IMAGE_FILE_INVALID);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new UsersException(UsersErrorCode.PROFILE_IMAGE_FILE_INVALID);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixels = (long) width * height;
                if (width > MAX_WIDTH || height > MAX_HEIGHT || pixels > MAX_PIXEL_COUNT) {
                    throw new UsersException(UsersErrorCode.PROFILE_IMAGE_FILE_INVALID);
                }
                decoded = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        if (decoded == null) {
            throw new UsersException(UsersErrorCode.PROFILE_IMAGE_FILE_INVALID);
        }

        BufferedImage normalized = decoded;
        if (imageType == ImageType.JPEG && decoded.getType() != BufferedImage.TYPE_INT_RGB) {
            normalized = new BufferedImage(decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = normalized.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, normalized.getWidth(), normalized.getHeight());
                graphics.drawImage(decoded, 0, 0, null);
            } finally {
                graphics.dispose();
            }
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(normalized, imageType.extension, output)) {
            throw new UsersException(UsersErrorCode.PROFILE_IMAGE_FILE_INVALID);
        }
        return output.toByteArray();
    }

    private enum ImageType {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png");

        private final String contentType;
        private final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        private static ImageType detect(byte[] bytes) {
            if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff) {
                return JPEG;
            }
            if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e
                    && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a
                    && bytes[7] == 0x0a) {
                return PNG;
            }
            return null;
        }
    }

    public record ValidatedProfileImage(byte[] bytes, String contentType, String extension) {
    }
}
