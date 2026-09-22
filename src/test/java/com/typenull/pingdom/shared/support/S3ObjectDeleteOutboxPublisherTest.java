package com.typenull.pingdom.shared.support;

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

    /**
     * Outbox 발행 대역을 주입해 S3 삭제 이벤트의 키·타입·집계 매핑만 검증.
     */
    @BeforeEach
    void setUp() {
        publisher = new S3ObjectDeleteOutboxPublisher(outboxEventPublisher);
    }

    /**
     * 450자 파일명 키도 S3_OBJECT_DELETE 접두사와 200자 이하 중복 방지 키로 발행되는지 검증.
     */
    @Test
    void boundsLongS3DeduplicationKey() {
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

    /**
     * 공백뿐인 S3 키는 삭제 Outbox를 발행하지 않는지 검증.
     */
    @Test
    void publishIgnoresBlankS3Key() {
        publisher.publish("   ", "MAP_IMAGE", "10", "MAP_IMAGE_DELETED");

        verify(outboxEventPublisher, never()).publish(any(), any(), any(), any(), any());
    }
}
