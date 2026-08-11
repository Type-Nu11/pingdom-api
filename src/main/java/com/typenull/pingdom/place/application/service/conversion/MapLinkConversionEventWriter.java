package com.typenull.pingdom.place.application.service.conversion;

import com.typenull.pingdom.place.domain.conversion.MapLinkConversionEvent;
import com.typenull.pingdom.place.infrastructure.persistence.conversion.MapLinkConversionEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class MapLinkConversionEventWriter {

    private final MapLinkConversionEventRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MapLinkConversionEvent insert(MapLinkConversionEvent event) {
        return repository.saveAndFlush(event);
    }
}
