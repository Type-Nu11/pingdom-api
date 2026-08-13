package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimReviewHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantPlaceClaimReviewHistoryRepository extends JpaRepository<MerchantPlaceClaimReviewHistory, Long> {
    List<MerchantPlaceClaimReviewHistory> findAllByClaimIdOrderByCreatedAtDescIdDesc(Long claimId);
}
