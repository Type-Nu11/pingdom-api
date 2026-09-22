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

/**
 * 업로드된 JPEG·PNG의 실제 바이트와 선언 타입을 대조하고 이미지 크기를 제한한다.
 * 픽셀 데이터를 다시 인코딩한 결과만 저장 계층에 전달하며 원본 파일명을 저장용 메타데이터로 정규화한다.
 */
@Component
public class VisitEvidenceFileValidator {
    private static final int MAX_WIDTH = 8_000;
    private static final int MAX_HEIGHT = 8_000;
    private static final long MAX_PIXEL_COUNT = 36_000_000L;
    private final VisitEvidenceProperties properties;

    public VisitEvidenceFileValidator(VisitEvidenceProperties properties) {
        this.properties = properties;
    }

    /**
     * 빈 파일, 용량 초과, 형식 불일치 또는 판독 불가 이미지를 도메인 오류로 거부한다.
     * 요청 크기·실제 바이트 길이·재인코딩 결과 크기를 각각 확인하여 어느 단계에서도 용량 제한을 넘지 않게 한다.
     */
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

    /**
     * 첫 번째 이미지의 너비·높이·픽셀 수를 먼저 확인한 뒤 디코딩하고 다시 인코딩한다.
     * 가로/세로 8,000 및 총 3,600만 픽셀을 초과하면 거부하고 JPEG는 RGB로 정규화한다.
     * 원본 스트림의 메타데이터를 그대로 복사하지 않으며 출력 인코더가 없으면 형식 오류로 처리한다.
     */
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
        // JPEG 출력에 맞지 않는 색상 모델은 흰 배경의 RGB 이미지에 그려 변환한다.
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

    /** MIME 타입의 앞뒤 공백과 대소문자를 정규화하며, 누락된 값은 빈 문자열로 비교한다. */
    private String normalize(String contentType) {
        return StringUtils.hasText(contentType) ? contentType.trim().toLowerCase(Locale.ROOT) : "";
    }

    /**
     * 양식에 포함된 경로 부분을 제거하고 빈 이름에는 evidence 확장자 이름을 사용한다.
     * 255자를 넘으면 뒤쪽 255자만 유지한다. 실제 S3 객체명과 별개의 원본 파일명 메타데이터다.
     */
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

        /** JPEG·PNG의 시작 시그니처를 구분한다. 이미지 본문의 유효성은 후속 디코딩에서 확인한다. */
        private static ImageType detect(byte[] bytes) {
            if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff) return JPEG;
            if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e
                    && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a
                    && bytes[7] == 0x0a) return PNG;
            return null;
        }
    }

    /** 용량·형식 검사를 통과해 재인코딩한 바이트와 정규화된 파일 메타데이터다. */
    public record ValidatedVisitEvidenceFile(byte[] bytes, String originalFilename, String contentType,
            String extension) {}
}
