package com.typenull.pingdom.shared.support;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
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
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있거나 존재하지 않습니다.");
        }

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

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, file.getSize()));
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

    public List<String> listKeys(String keyPrefix) {
        // 지정한 prefix 아래의 모든 S3 객체 key를 페이지 단위로 모은다.
        if (!StringUtils.hasText(bucket)) {
            throw new S3StorageException(S3StorageError.NOT_CONFIGURED, "S3 bucket is not configured.", null);
        }

        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            throw new S3StorageException(S3StorageError.NOT_CONFIGURED, "S3 client is not configured.", null);
        }

        String prefix = StringUtils.hasText(keyPrefix) ? keyPrefix.trim() : "";
        if (StringUtils.hasText(prefix) && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }

        List<String> keys = new ArrayList<>();
        String continuationToken = null;
        try {
            do {
                ListObjectsV2Request request = ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .continuationToken(continuationToken)
                        .build();
                ListObjectsV2Response response = s3Client.listObjectsV2(request);
                response.contents().forEach(object -> keys.add(object.key()));
                continuationToken = response.nextContinuationToken();
            } while (continuationToken != null);
            return keys;
        } catch (S3Exception exception) {
            log.error("S3 목록 조회 실패: {}", exception.awsErrorDetails() == null ? exception.getMessage() : exception.awsErrorDetails().errorMessage());
            throw new S3StorageException(S3StorageError.S3_ERROR, "S3 listObjectsV2 failed.", exception);
        } catch (SdkException exception) {
            log.error("S3 연결 실패: {}", exception.getMessage());
            throw new S3StorageException(S3StorageError.CONNECTION_ERROR, "S3 connection failed.", exception);
        }
    }

    public S3KeyPage listKeysPage(String keyPrefix, String continuationToken) {
        if (!StringUtils.hasText(bucket)) {
            throw new S3StorageException(S3StorageError.NOT_CONFIGURED, "S3 bucket is not configured.", null);
        }

        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            throw new S3StorageException(S3StorageError.NOT_CONFIGURED, "S3 client is not configured.", null);
        }

        String prefix = StringUtils.hasText(keyPrefix) ? keyPrefix.trim() : "";
        if (StringUtils.hasText(prefix) && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }

        try {
            ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .continuationToken(continuationToken)
                    .build());
            List<String> keys = response.contents()
                    .stream()
                    .map(object -> object.key())
                    .toList();
            return new S3KeyPage(keys, response.nextContinuationToken());
        } catch (S3Exception exception) {
            log.error("S3 목록 조회 실패: {}", exception.awsErrorDetails() == null ? exception.getMessage() : exception.awsErrorDetails().errorMessage());
            throw new S3StorageException(S3StorageError.S3_ERROR, "S3 listObjectsV2 failed.", exception);
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

    public record S3KeyPage(List<String> keys, String nextContinuationToken) {
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
