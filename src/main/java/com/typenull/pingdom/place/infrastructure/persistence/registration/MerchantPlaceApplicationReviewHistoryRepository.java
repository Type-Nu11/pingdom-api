package com.typenull.pingdom.place.infrastructure.persistence.registration;

import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationReviewHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantPlaceApplicationReviewHistoryRepository
        extends JpaRepository<MerchantPlaceApplicationReviewHistory, Long> {

    List<MerchantPlaceApplicationReviewHistory> findAllByApplicationIdOrderByCreatedAtDescIdDesc(Long applicationId);
}
