package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationSummary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceInformationVerificationSummaryRepository
        extends JpaRepository<PlaceInformationVerificationSummary, Long> {
}
