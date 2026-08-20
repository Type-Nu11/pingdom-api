package com.typenull.pingdom.shared.support;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Component
@RequiredArgsConstructor
@Slf4j
public class S3ObjectStorage {

    private final ObjectProvider<S3Client> s3ClientProvider;
    private final ObjectProvider<S3Presigner> s3PresignerProvider;

    @Value("${spring.cloud.aws.s3.bucket:}")
    private String bucket;

    public S3PutResult put(MultipartFile file, String keyPrefix) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있거나 존재하지 않습니다.");
        }

        try (InputStream inputStream = file.getInputStream()) {
            return put(
                    inputStream,
                    file.getSize(),
                    file.getOriginalFilename(),
                    file.getContentType(),
                    keyPrefix
            );
        }
    }

    public S3PutResult put(byte[] content, String originalFilename, String contentType, String keyPrefix) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("파일이 비어있거나 존재하지 않습니다.");
        }

        return put(
                new java.io.ByteArrayInputStream(content),
                content.length,
                originalFilename,
                contentType,
                keyPrefix
        );
    }

    public S3PutResult putPrivate(byte[] content, String contentType, String keyPrefix) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("파일이 비어있거나 존재하지 않습니다.");
        }
        return put(new java.io.ByteArrayInputStream(content), content.length, null, contentType, keyPrefix);
    }

    public PresignedPutResult presignedPut(String key, String contentType) {
        S3Presigner presigner = s3PresignerProvider.getIfAvailable();
        if (presigner == null || !StringUtils.hasText(bucket)) {
            throw new S3StorageException(S3StorageError.NOT_CONFIGURED, "S3 presigner is not configured.", null);
        }
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        String uploadUrl = presigner.presignPutObject(builder -> builder
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(request)
        ).url().toExternalForm();
        String imageUrl = s3Client().utilities()
                .getUrl(GetUrlRequest.builder().bucket(bucket).key(key).build())
                .toExternalForm();
        return new PresignedPutResult(key, uploadUrl, imageUrl, expiresAt);
    }

    private S3PutResult put(
            InputStream inputStream,
            long contentLength,
            String originalFilename,
            String contentType,
            String keyPrefix
    ) {
        S3Client s3Client = s3Client();

        String prefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "";
        if (StringUtils.hasText(prefix) && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }

        String key = prefix + UUID.randomUUID() + "-" + (originalFilename != null ? originalFilename : "unnamed");

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentLength(contentLength)
                .contentType(contentType)
                .build();

        try {
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength));
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

        S3Client s3Client = s3Client();

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

    public byte[] getBytes(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("S3 객체 key가 비어 있습니다.");
        }
        try {
            return s3Client().getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key.trim()).build())
                    .asByteArray();
        } catch (S3Exception exception) {
            log.error("S3 조회 실패: {}", exception.awsErrorDetails() == null
                    ? exception.getMessage() : exception.awsErrorDetails().errorMessage());
            throw new S3StorageException(S3StorageError.S3_ERROR, "S3 getObject failed.", exception);
        } catch (SdkException exception) {
            log.error("S3 연결 실패: {}", exception.getMessage());
            throw new S3StorageException(S3StorageError.CONNECTION_ERROR, "S3 connection failed.", exception);
        }
    }

    public S3ListResult listKeys(String keyPrefix, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("조회 제한은 1 이상이어야 합니다.");
        }

        S3Client s3Client = s3Client();
        String prefix = normalizePrefix(keyPrefix);
        List<String> keys = new ArrayList<>();
        String continuationToken = null;

        try {
            do {
                int remaining = limit - keys.size();
                ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .maxKeys(Math.min(1_000, remaining));
                if (StringUtils.hasText(continuationToken)) {
                    requestBuilder.continuationToken(continuationToken);
                }

                ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());
                response.contents().forEach(object -> keys.add(object.key()));
                continuationToken = response.nextContinuationToken();
            } while (StringUtils.hasText(continuationToken) && keys.size() < limit);

            return new S3ListResult(List.copyOf(keys), StringUtils.hasText(continuationToken));
        } catch (S3Exception exception) {
            log.error("S3 객체 목록 조회 실패: {}", exception.awsErrorDetails() == null ? exception.getMessage() : exception.awsErrorDetails().errorMessage());
            throw new S3StorageException(S3StorageError.S3_ERROR, "S3 listObjectsV2 failed.", exception);
        } catch (SdkException exception) {
            log.error("S3 연결 실패: {}", exception.getMessage());
            throw new S3StorageException(S3StorageError.CONNECTION_ERROR, "S3 connection failed.", exception);
        }
    }

    public List<String> listKeys(String keyPrefix) {
        // 지정한 prefix 아래의 모든 S3 객체 key를 페이지 단위로 모은다.
        S3Client s3Client = s3Client();
        String prefix = normalizePrefix(keyPrefix);
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
        S3Client s3Client = s3Client();
        String prefix = normalizePrefix(keyPrefix);

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

    private S3Client s3Client() {
        if (!StringUtils.hasText(bucket)) {
            throw new S3StorageException(S3StorageError.NOT_CONFIGURED, "S3 bucket is not configured.", null);
        }

        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            throw new S3StorageException(S3StorageError.NOT_CONFIGURED, "S3 client is not configured.", null);
        }
        return s3Client;
    }

    private String normalizePrefix(String keyPrefix) {
        String prefix = StringUtils.hasText(keyPrefix) ? keyPrefix.trim() : "";
        if (StringUtils.hasText(prefix) && !prefix.endsWith("/")) {
            return prefix + "/";
        }
        return prefix;
    }

    public enum S3StorageError {
        NOT_CONFIGURED,
        S3_ERROR,
        CONNECTION_ERROR
    }

    public record S3PutResult(String key, String url) {
    }

    public record PresignedPutResult(String key, String uploadUrl, String imageUrl, LocalDateTime expiresAt) {
    }

    public record S3ListResult(List<String> keys, boolean truncated) {
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
