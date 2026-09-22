package com.typenull.pingdom.place.application.service.conversion;

import com.typenull.pingdom.place.domain.conversion.MapLinkConversionEvent;
import com.typenull.pingdom.place.infrastructure.persistence.conversion.MapLinkConversionEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지도 링크 전환 insert를 새 트랜잭션과 즉시 flush로 분리합니다.
 * 중복 insert 실패가 호출자의 재조회 트랜잭션까지 rollback-only로 만들지 않도록 별도 빈에서 호출합니다.
 */
@Component
@RequiredArgsConstructor
class MapLinkConversionEventWriter {

    private final MapLinkConversionEventRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MapLinkConversionEvent insert(MapLinkConversionEvent event) {
        return repository.saveAndFlush(event);
    }
}
