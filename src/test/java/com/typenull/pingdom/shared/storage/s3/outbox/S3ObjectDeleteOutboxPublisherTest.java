package com.typenull.pingdom.shared.storage.s3.outbox;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class S3ObjectDeleteOutboxPublisherTest {

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private S3ObjectDeleteOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new S3ObjectDeleteOutboxPublisher(outboxEventPublisher);
    }

    @Test
    void publishUsesBoundedDeduplicationKeyForLongS3Key() {
        String longS3Key = "map/" + "a".repeat(450) + ".jpg";
        ArgumentCaptor<String> deduplicationKeyCaptor = ArgumentCaptor.forClass(String.class);

        publisher.publish(longS3Key, "MAP_IMAGE", "10", "MAP_IMAGE_DELETED");

        verify(outboxEventPublisher).publish(
                deduplicationKeyCaptor.capture(),
                eq(OutboxEventType.S3_OBJECT_DELETE_REQUESTED),
                any(S3ObjectDeleteOutboxPayload.class),
                eq("MAP_IMAGE"),
                eq("10")
        );
        assertTrue(deduplicationKeyCaptor.getValue().startsWith("S3_OBJECT_DELETE:"));
        assertTrue(deduplicationKeyCaptor.getValue().length() <= 200);
    }

    @Test
    void publishIgnoresBlankS3Key() {
        publisher.publish("   ", "MAP_IMAGE", "10", "MAP_IMAGE_DELETED");

        verify(outboxEventPublisher, never()).publish(any(), any(), any(), any(), any());
    }
}
