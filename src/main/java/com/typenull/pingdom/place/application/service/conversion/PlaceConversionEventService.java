package com.typenull.pingdom.place.application.service.conversion;

import com.typenull.pingdom.place.domain.conversion.PlaceConversionEvent;
import com.typenull.pingdom.place.domain.conversion.PlaceConversionEventType;
import com.typenull.pingdom.place.infrastructure.persistence.conversion.PlaceConversionEventRepository;
import com.typenull.pingdom.place.outbox.PlaceConversionEventOutboxPayload;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유형과 원천 ID를 기준으로 장소 전환 이력과 후속 처리 outbox 이벤트를 함께 저장.
 * 사전 조회에서 찾은 중복은 생략하며 동시 insert의 제약 위반은 별도 복구 대상에서 제외.
 */
@Service
@RequiredArgsConstructor
public class PlaceConversionEventService {

    private final PlaceConversionEventRepository conversionEventRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    /**
     * 전환 유형과 원본 ID로 중복 키를 만들고 이미 기록된 전환이면 아무 작업 없이 반환.
     * 새 전환 행과 전달용 outbox 이벤트를 같은 트랜잭션에 저장하며 outbox 소비 완료까지의 대기는 생략.
     * 중복 사전 조회의 동시 삽입 직렬화는 보장 범위에서 제외되며 저장·발행 실패는 호출자에게 전파.
     */
    @Transactional
    public void publish(
            Long userId,
            Long placeId,
            PlaceConversionEventType conversionType,
            Long sourceId,
            LocalDateTime occurredAt
    ) {
        String deduplicationKey = "PLACE_CONVERSION_EVENT:%s:%d".formatted(conversionType, sourceId);
        if (conversionEventRepository.findByDeduplicationKey(deduplicationKey).isPresent()) {
            return;
        }

        PlaceConversionEvent event = conversionEventRepository.save(
                PlaceConversionEvent.create(
                        userId,
                        placeId,
                        conversionType,
                        sourceId,
                        deduplicationKey,
                        occurredAt,
                        occurredAt
                )
        );
        outboxEventPublisher.publish(
                deduplicationKey,
                OutboxEventType.PLACE_CONVERSION_RECORDED,
                new PlaceConversionEventOutboxPayload(
                        event.getId(), userId, placeId, conversionType, sourceId, occurredAt
                ),
                "PLACE_CONVERSION_EVENT",
                String.valueOf(event.getId())
        );
    }
}
