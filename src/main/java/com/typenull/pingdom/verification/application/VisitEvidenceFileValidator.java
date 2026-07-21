package com.typenull.pingdom.verification.application;

import com.typenull.pingdom.verification.domain.exception.*;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
public class VisitEvidenceFileValidator {
    private static final int MAX_WIDTH = 8_000;
    private static final int MAX_HEIGHT = 8_000;
    private static final long MAX_PIXEL_COUNT = 36_000_000L;
    private final VisitEvidenceProperties properties;

    public VisitEvidenceFileValidator(VisitEvidenceProperties properties) {
        this.properties = properties;
    }

    public ValidatedVisitEvidenceFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_EMPTY);
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_TOO_LARGE);
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > properties.maxFileSizeBytes()) {
                throw new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_TOO_LARGE);
            }
            ImageType type = ImageType.detect(bytes);
            if (type == null || !type.contentType.equals(normalize(file.getContentType()))) {
                throw new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_INVALID);
            }
            byte[] processedBytes = decodeAndReencode(bytes, type);
            if (processedBytes.length > properties.maxFileSizeBytes()) {
                throw new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_TOO_LARGE);
            }
            String originalFilename = normalizeFilename(file.getOriginalFilename(), type.extension);
            return new ValidatedVisitEvidenceFile(processedBytes, originalFilename, type.contentType, type.extension);
        } catch (IOException exception) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_INVALID);
        }
    }

    private byte[] decodeAndReencode(byte[] bytes, ImageType type) throws IOException {
        ImageIO.setUseCache(false);
        BufferedImage decoded;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_INVALID);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_INVALID);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixels = (long) width * height;
                if (width > MAX_WIDTH || height > MAX_HEIGHT || pixels > MAX_PIXEL_COUNT) {
                    throw new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_INVALID);
                }
                decoded = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        if (decoded == null) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_INVALID);
        }
        BufferedImage normalized = decoded;
        if (type == ImageType.JPEG && decoded.getType() != BufferedImage.TYPE_INT_RGB) {
            normalized = new BufferedImage(decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = normalized.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, normalized.getWidth(), normalized.getHeight());
            graphics.drawImage(decoded, 0, 0, null);
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(normalized, type.extension, output)) {
            throw new VisitorVerificationException(VisitorVerificationErrorCode.VISIT_EVIDENCE_FILE_INVALID);
        }
        return output.toByteArray();
    }

    private String normalize(String contentType) {
        return StringUtils.hasText(contentType) ? contentType.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String normalizeFilename(String filename, String extension) {
        String normalized = StringUtils.hasText(filename) ? filename.trim().replace('\\', '/') : "";
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (!StringUtils.hasText(normalized)) normalized = "evidence." + extension;
        return normalized.length() <= 255 ? normalized : normalized.substring(normalized.length() - 255);
    }

    private enum ImageType {
        JPEG("image/jpeg", "jpg"), PNG("image/png", "png");

        private final String contentType;
        private final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        private static ImageType detect(byte[] bytes) {
            if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff) return JPEG;
            if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e
                    && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a
                    && bytes[7] == 0x0a) return PNG;
            return null;
        }
    }

    public record ValidatedVisitEvidenceFile(byte[] bytes, String originalFilename, String contentType,
            String extension) {}
}
