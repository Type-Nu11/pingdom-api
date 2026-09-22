package com.typenull.pingdom.shared.support;

import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageError;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class S3ObjectDeleteOutboxHandlerTest {

    @Mock
    private S3ObjectStorage s3ObjectStorage;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private S3ObjectDeleteOutboxHandler handler;

    /**
     * 실제 JSON 역직렬화기와 S3 대역을 연결해 삭제 Outbox 처리를 검증.
     */
    @BeforeEach
    void setUp() {
        handler = new S3ObjectDeleteOutboxHandler(s3ObjectStorage, objectMapper);
    }

    /**
     * 삭제 payload의 s3Key를 해석하여 지정 S3 객체를 삭제하는지 검증.
     */
    @Test
    void handleDeletesS3Object() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new S3ObjectDeleteOutboxPayload("map/delete-target.jpg", "MAP_IMAGE_DELETED")
        );

        handler.handle("event-1", payload);

        verify(s3ObjectStorage).delete("map/delete-target.jpg");
    }

    /**
     * S3 연결 오류가 핸들러 밖으로 전파되어 Outbox의 실패·재시도 경로에서 관측되는지 검증.
     */
    @Test
    void propagatesRetryableS3Failure() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new S3ObjectDeleteOutboxPayload("map/delete-target.jpg", "MAP_IMAGE_DELETED")
        );
        org.mockito.Mockito.doThrow(new S3StorageException(
                        S3StorageError.CONNECTION_ERROR,
                        "temporary s3 failure",
                        null
                ))
                .when(s3ObjectStorage)
                .delete("map/delete-target.jpg");

        assertThrows(S3StorageException.class, () -> handler.handle("event-1", payload));
    }

    /**
     * S3 미설정 오류는 예외 없이 건너뛰어 미설정 환경의 삭제 이벤트를 계속 실패시키지 않는지 검증.
     */
    @Test
    void skipsUnconfiguredS3Deletion() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new S3ObjectDeleteOutboxPayload("map/delete-target.jpg", "MAP_IMAGE_DELETED")
        );
        org.mockito.Mockito.doThrow(new S3StorageException(
                        S3StorageError.NOT_CONFIGURED,
                        "s3 is not configured",
                        null
                ))
                .when(s3ObjectStorage)
                .delete("map/delete-target.jpg");

        assertDoesNotThrow(() -> handler.handle("event-1", payload));
    }
}
