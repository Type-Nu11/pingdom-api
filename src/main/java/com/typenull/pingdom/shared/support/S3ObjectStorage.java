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
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 선택적으로 구성된 S3 client·presigner로 객체 입출력과 목록 조회를 수행한다.
 * 객체 접근 공개 여부는 버킷 정책에 달려 있으며 URL 생성만으로 공개 접근이나 객체 존재를 보장하지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class S3ObjectStorage {

    private final ObjectProvider<S3Client> s3ClientProvider;
    private final ObjectProvider<S3Presigner> s3PresignerProvider;

    @Value("${spring.cloud.aws.s3.bucket:}")
    private String bucket;

    /** multipart 스트림을 열어 업로드하고 닫는다. 원본 파일명은 UUID 뒤에 붙이며 빈 파일은 거부한다. */
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

    /** 메모리의 파일 내용을 지정 prefix 아래 새 UUID key로 업로드한다. DB 저장 실패에 대한 보상 삭제는 호출자 책임이다. */
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

    /** 원본 파일명 없이 업로드한다. 별도 private ACL을 지정하지 않으므로 호출자가 private prefix와 버킷 정책을 사용해야 한다. */
    public S3PutResult putPrivate(byte[] content, String contentType, String keyPrefix) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("파일이 비어있거나 존재하지 않습니다.");
        }
        return put(new java.io.ByteArrayInputStream(content), content.length, null, contentType, keyPrefix);
    }

    /** 지정 key·Content-Type의 PUT 서명을 10분간 발급한다. expiresAt은 UTC이며 URL 발급 시 객체 업로드는 수행하지 않는다. */
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

    /** 공백 key는 무시하고 나머지는 DeleteObject로 제거한다. S3 응답 오류와 SDK 연결 오류를 구분해 전파한다. */
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

    /**
     * 같은 버킷의 sourceKey 객체를 targetKey로 복사한다. 비어 있는 key는 거절하고 양끝 공백을 제거한다.
     * 공개 prefix 선택은 호출자가 담당하며 이 메서드는 ACL·버킷 정책을 변경하거나 공개 접근을 확인하지 않는다.
     */
    public void copy(String sourceKey, String targetKey) {
        if (!StringUtils.hasText(sourceKey) || !StringUtils.hasText(targetKey)) {
            throw new IllegalArgumentException("S3 복사 source/target key가 비어 있습니다.");
        }
        String source = sourceKey.trim();
        String target = targetKey.trim();
        try {
            s3Client().copyObject(CopyObjectRequest.builder()
                    .sourceBucket(bucket)
                    .sourceKey(source)
                    .destinationBucket(bucket)
                    .destinationKey(target)
                    .build());
        } catch (S3Exception exception) {
            log.error("S3 복사 실패: {} -> {}", source, target);
            throw new S3StorageException(S3StorageError.S3_ERROR, "S3 copyObject failed.", exception);
        } catch (SdkException exception) {
            log.error("S3 연결 실패: {}", exception.getMessage());
            throw new S3StorageException(S3StorageError.CONNECTION_ERROR, "S3 connection failed.", exception);
        }
    }

    /** 객체 본문 없이 길이(바이트)와 Content-Type을 조회한다. 존재하지 않는 객체도 S3 오류로 변환한다. */
    public S3ObjectMetadata headObject(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("S3 객체 key가 비어 있습니다.");
        }

        try {
            HeadObjectResponse response = s3Client().headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key.trim())
                    .build());
            return new S3ObjectMetadata(response.contentLength(), response.contentType());
        } catch (S3Exception exception) {
            log.error("S3 객체 메타데이터 조회 실패: {}", exception.awsErrorDetails() == null
                    ? exception.getMessage() : exception.awsErrorDetails().errorMessage());
            throw new S3StorageException(S3StorageError.S3_ERROR, "S3 headObject failed.", exception);
        } catch (SdkException exception) {
            log.error("S3 연결 실패: {}", exception.getMessage());
            throw new S3StorageException(S3StorageError.CONNECTION_ERROR, "S3 connection failed.", exception);
        }
    }

    /** key의 S3 URL을 계산한다. 객체 존재·익명 접근 가능 여부는 조회하지 않는다. */
    public String publicUrl(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("S3 객체 key가 비어 있습니다.");
        }
        return s3Client().utilities()
                .getUrl(GetUrlRequest.builder().bucket(bucket).key(key.trim()).build())
                .toExternalForm();
    }

    /** 객체 전체를 메모리에 읽는다. 크기 제한은 적용하지 않으므로 호출자가 대상 크기를 통제해야 한다. */
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

    /** prefix를 폴더 경계로 정규화하고 최대 limit개 key를 페이지별로 수집한다. 남은 continuation token이 있으면 truncated가 true다. */
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

    /** 모든 페이지의 key를 메모리에 모은다. 대량 객체 처리는 제한 조회 또는 listKeysPage 사용을 고려해야 한다. */
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

    /** 한 페이지만 조회하고 다음 continuation token을 그대로 반환한다. null 토큰은 목록 종료를 의미한다. */
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

    /** 주변 공백을 제거하고 비어 있지 않은 prefix에 슬래시를 붙여 이름 일부가 같은 다른 폴더를 제외한다. */
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

    /** 업로드 서명 URL과 계산된 객체 URL, UTC 기준 만료 시각이다. uploadUrl은 만료 전 민감한 쓰기 권한을 포함한다. */
    public record PresignedPutResult(String key, String uploadUrl, String imageUrl, LocalDateTime expiresAt) {
    }

    /** S3가 응답한 콘텐츠 길이(바이트)와 MIME 유형이다. */
    public record S3ObjectMetadata(Long contentLength, String contentType) {
    }

    /** 제한 개수까지 모은 key와 미조회 페이지 존재 여부다. 전체 버킷 개수나 재개 토큰은 포함하지 않는다. */
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
