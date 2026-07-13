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

    @BeforeEach
    void setUp() {
        handler = new S3ObjectDeleteOutboxHandler(s3ObjectStorage, objectMapper);
    }

    @Test
    void handleDeletesS3Object() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new S3ObjectDeleteOutboxPayload("map/delete-target.jpg", "MAP_IMAGE_DELETED")
        );

        handler.handle("event-1", payload);

        verify(s3ObjectStorage).delete("map/delete-target.jpg");
    }

    @Test
    void s3FailureIsPropagatedForOutboxRetry() throws Exception {
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

    @Test
    void s3NotConfiguredIsTreatedAsSuccessfulSkip() throws Exception {
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
