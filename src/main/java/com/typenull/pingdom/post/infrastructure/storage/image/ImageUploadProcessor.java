package com.typenull.pingdom.post.infrastructure.storage.image;

import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ImageUploadProcessor {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_WIDTH = 8_000;
    private static final int MAX_HEIGHT = 8_000;
    private static final long MAX_PIXEL_COUNT = 36_000_000L;
    private static final int THUMBNAIL_MAX_WIDTH = 512;
    private static final int THUMBNAIL_MAX_HEIGHT = 512;
    private static final float JPEG_QUALITY = 0.9f;

    public ProcessedImageUpload process(MultipartFile file) {
        byte[] uploadBytes = readAndValidateSize(file);
        ImageUploadFormat format = ImageUploadFormat.detect(uploadBytes);
        validateDeclaredContentType(file.getContentType(), format);

        BufferedImage image = decode(uploadBytes);
        validateDimensions(image);

        BufferedImage normalizedOriginal = normalizeForFormat(image, format);
        byte[] originalBytes = writeWithoutMetadata(normalizedOriginal, format);

        BufferedImage thumbnail = resize(image, format);
        byte[] thumbnailBytes = writeWithoutMetadata(thumbnail, format);

        String baseFilename = baseFilename(file.getOriginalFilename());
        return new ProcessedImageUpload(
                originalBytes,
                baseFilename + "-processed." + format.extension(),
                format.contentType(),
                thumbnailBytes,
                baseFilename + "-thumbnail." + format.extension(),
                format.contentType(),
                image.getWidth(),
                image.getHeight(),
                thumbnail.getWidth(),
                thumbnail.getHeight()
        );
    }

    private byte[] readAndValidateSize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MapException(MapErrorCode.IMAGE_FILE_EMPTY);
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new MapException(MapErrorCode.IMAGE_FILE_TOO_LARGE);
        }

        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0) {
                throw new MapException(MapErrorCode.IMAGE_FILE_EMPTY);
            }
            return bytes;
        } catch (IOException exception) {
            throw new MapException(MapErrorCode.UPLOAD_ERROR);
        }
    }

    private void validateDeclaredContentType(String contentType, ImageUploadFormat format) {
        if (!StringUtils.hasText(contentType)) {
            throw new MapException(MapErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        if (!format.matchesContentType(contentType)) {
            throw new MapException(MapErrorCode.INVALID_IMAGE_FILE);
        }
    }

    private BufferedImage decode(byte[] bytes) {
        ImageIO.setUseCache(false);
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new MapException(MapErrorCode.INVALID_IMAGE_FILE);
            }
            return image;
        } catch (IOException exception) {
            throw new MapException(MapErrorCode.INVALID_IMAGE_FILE);
        }
    }

    private void validateDimensions(BufferedImage image) {
        long pixels = (long) image.getWidth() * (long) image.getHeight();
        if (image.getWidth() > MAX_WIDTH || image.getHeight() > MAX_HEIGHT || pixels > MAX_PIXEL_COUNT) {
            throw new MapException(MapErrorCode.IMAGE_RESOLUTION_TOO_LARGE);
        }
    }

    private BufferedImage resize(BufferedImage source, ImageUploadFormat format) {
        double scale = Math.min(
                1.0d,
                Math.min(
                        (double) THUMBNAIL_MAX_WIDTH / (double) source.getWidth(),
                        (double) THUMBNAIL_MAX_HEIGHT / (double) source.getHeight()
                )
        );
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage target = new BufferedImage(targetWidth, targetHeight, imageType(format));
        Graphics2D graphics = target.createGraphics();
        try {
            if (format == ImageUploadFormat.JPEG) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
            }
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
            return target;
        } finally {
            graphics.dispose();
        }
    }

    private BufferedImage normalizeForFormat(BufferedImage source, ImageUploadFormat format) {
        if (format != ImageUploadFormat.JPEG) {
            return source;
        }

        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.drawImage(source, 0, 0, null);
            return target;
        } finally {
            graphics.dispose();
        }
    }

    private byte[] writeWithoutMetadata(BufferedImage image, ImageUploadFormat format) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format.writerFormatName());
        if (!writers.hasNext()) {
            throw new MapException(MapErrorCode.UPLOAD_ERROR);
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(imageOutputStream);
            writer.write(null, new IIOImage(image, null, null), writeParam(writer, format));
            imageOutputStream.flush();
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new MapException(MapErrorCode.UPLOAD_ERROR);
        } finally {
            writer.dispose();
        }
    }

    private ImageWriteParam writeParam(ImageWriter writer, ImageUploadFormat format) {
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (format == ImageUploadFormat.JPEG && param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
        }
        return param;
    }

    private int imageType(ImageUploadFormat format) {
        return format == ImageUploadFormat.JPEG ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
    }

    private String baseFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "image";
        }

        String filename = originalFilename.replace("\\", "/");
        int pathSeparator = filename.lastIndexOf('/');
        if (pathSeparator >= 0) {
            filename = filename.substring(pathSeparator + 1);
        }

        int extensionSeparator = filename.lastIndexOf('.');
        String base = extensionSeparator > 0 ? filename.substring(0, extensionSeparator) : filename;
        String normalizedBase = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        return StringUtils.hasText(normalizedBase) ? normalizedBase : "image";
    }

    private enum ImageUploadFormat {
        JPEG("image/jpeg", "jpg", "jpeg"),
        PNG("image/png", "png", "png");

        private final String contentType;
        private final String extension;
        private final String writerFormatName;

        ImageUploadFormat(String contentType, String extension, String writerFormatName) {
            this.contentType = contentType;
            this.extension = extension;
            this.writerFormatName = writerFormatName;
        }

        static ImageUploadFormat detect(byte[] bytes) {
            if (bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xFF
                    && (bytes[1] & 0xFF) == 0xD8
                    && (bytes[2] & 0xFF) == 0xFF) {
                return JPEG;
            }

            if (bytes.length >= 8
                    && (bytes[0] & 0xFF) == 0x89
                    && bytes[1] == 0x50
                    && bytes[2] == 0x4E
                    && bytes[3] == 0x47
                    && bytes[4] == 0x0D
                    && bytes[5] == 0x0A
                    && bytes[6] == 0x1A
                    && bytes[7] == 0x0A) {
                return PNG;
            }

            throw new MapException(MapErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }

        boolean matchesContentType(String value) {
            String normalized = value.toLowerCase(Locale.ROOT).trim();
            return contentType.equals(normalized)
                    || (this == JPEG && "image/jpg".equals(normalized));
        }

        String contentType() {
            return contentType;
        }

        String extension() {
            return extension;
        }

        String writerFormatName() {
            return writerFormatName;
        }
    }
}
