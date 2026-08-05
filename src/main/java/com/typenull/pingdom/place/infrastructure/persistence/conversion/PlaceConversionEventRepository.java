package com.typenull.pingdom.place.infrastructure.persistence.conversion;

import com.typenull.pingdom.place.domain.conversion.PlaceConversionEvent;
import com.typenull.pingdom.place.domain.conversion.PlaceConversionEventType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceConversionEventRepository extends JpaRepository<PlaceConversionEvent, Long> {

    boolean existsByConversionTypeAndSourceId(PlaceConversionEventType conversionType, Long sourceId);

    Optional<PlaceConversionEvent> findByDeduplicationKey(String deduplicationKey);
}
