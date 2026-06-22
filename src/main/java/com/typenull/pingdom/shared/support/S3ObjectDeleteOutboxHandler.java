package com.typenull.pingdom.shared.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.shared.outbox.application.OutboxEventHandler;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class S3ObjectDeleteOutboxHandler implements OutboxEventHandler {

    private final S3ObjectStorage s3ObjectStorage;
    private final ObjectMapper objectMapper;

    @Override
    public OutboxEventType supportedType() {
        return OutboxEventType.S3_OBJECT_DELETE_REQUESTED;
    }

    @Override
    public void handle(String eventId, String payload) {
        S3ObjectDeleteOutboxPayload event = deserialize(payload);
        if (!StringUtils.hasText(event.s3Key())) {
            throw new IllegalArgumentException("S3 삭제 Outbox payload에 s3Key가 없습니다.");
        }

        String s3Key = event.s3Key().trim();
        try {
            s3ObjectStorage.delete(s3Key);
            log.info(
                    "S3 삭제 Outbox 처리 성공. eventId={}, s3Key={}, reason={}",
                    eventId,
                    s3Key,
                    event.reason()
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "S3 삭제 Outbox 처리 실패. eventId={}, s3Key={}, reason={}, error={}",
                    eventId,
                    s3Key,
                    event.reason(),
                    exception.getMessage()
            );
            throw exception;
        }
    }

    private S3ObjectDeleteOutboxPayload deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, S3ObjectDeleteOutboxPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("S3 삭제 Outbox payload 역직렬화에 실패했습니다.", exception);
        }
    }
}
