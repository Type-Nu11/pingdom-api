package com.typenull.pingdom.shared.support;

import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@RequiredArgsConstructor
@Slf4j
public class S3ObjectStorage {

    private final ObjectProvider<S3Client> s3ClientProvider;

    @Value("${spring.cloud.aws.s3.bucket:}")
    private String bucket;

    public S3PutResult put(MultipartFile file, String keyPrefix) throws IOException {
        if (!StringUtils.hasText(bucket)) {
            throw new S3StorageException(S3StorageError.NOT_CONFIGURED, "S3 bucket is not configured.", null);
        }

        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            throw new S3StorageException(S3StorageError.NOT_CONFIGURED, "S3 client is not configured.", null);
        }

        String prefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "";
        if (StringUtils.hasText(prefix) && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }

        String originalFilename = file.getOriginalFilename();
        String key = prefix + UUID.randomUUID() + "-" + (originalFilename != null ? originalFilename : "unnamed");

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentLength(file.getSize())
                .contentType(file.getContentType())
                .build();

        try {
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            String url = s3Client.utilities()
                    .getUrl(GetUrlRequest.builder().bucket(bucket).key(key).build())
                    .toExternalForm();
            return new S3PutResult(key, url);
        } catch (S3Exception exception) {
            log.error("S3 업로드 실패: {}", exception.awsErrorDetails() == null ? exception.getMessage() : exception.awsErrorDetails().errorMessage());
            throw new S3StorageException(S3StorageError.S3_ERROR, "S3 putObject failed.", exception);
        } catch (SdkException exception) {
            log.error("S3 연결 실패: {}", exception.getMessage());
            throw new S3StorageException(S3StorageError.CONNECTION_ERROR, "S3 connection failed.", exception);
        }
    }

    public void delete(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }

        if (!StringUtils.hasText(bucket)) {
            throw new S3StorageException(S3StorageError.NOT_CONFIGURED, "S3 bucket is not configured.", null);
        }

        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            throw new S3StorageException(S3StorageError.NOT_CONFIGURED, "S3 client is not configured.", null);
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception exception) {
            log.error("S3 삭제 실패: {}", exception.awsErrorDetails() == null ? exception.getMessage() : exception.awsErrorDetails().errorMessage());
            throw new S3StorageException(S3StorageError.S3_ERROR, "S3 deleteObject failed.", exception);
        } catch (SdkException exception) {
            log.error("S3 연결 실패: {}", exception.getMessage());
            throw new S3StorageException(S3StorageError.CONNECTION_ERROR, "S3 connection failed.", exception);
        }
    }

    public enum S3StorageError {
        NOT_CONFIGURED,
        S3_ERROR,
        CONNECTION_ERROR
    }

    public record S3PutResult(String key, String url) {
    }

    public static class S3StorageException extends RuntimeException {
        private final S3StorageError error;

        public S3StorageException(S3StorageError error, String message, Throwable cause) {
            super(message, cause);
            this.error = error;
        }

        public S3StorageError getError() {
            return error;
        }
    }
}

