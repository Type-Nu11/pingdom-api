package com.typenull.pingdom.place.infrastructure.persistence.conversion;

import com.typenull.pingdom.place.domain.conversion.MapLinkConversionEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapLinkConversionEventRepository extends JpaRepository<MapLinkConversionEvent, Long> {
    Optional<MapLinkConversionEvent> findByDeduplicationKey(String deduplicationKey);
}
