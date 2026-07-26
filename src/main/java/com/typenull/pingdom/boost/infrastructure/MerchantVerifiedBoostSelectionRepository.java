package com.typenull.pingdom.boost.infrastructure;

import com.typenull.pingdom.boost.domain.MerchantVerifiedBoostSelection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantVerifiedBoostSelectionRepository
        extends JpaRepository<MerchantVerifiedBoostSelection, Long> {

    Optional<MerchantVerifiedBoostSelection> findByIdAndMerchantOwnerUserId(Long id, Long ownerId);

    Optional<MerchantVerifiedBoostSelection> findByMerchantOwnerUserIdAndPlaceIdAndIdempotencyKey(
            Long ownerId, Long placeId, String idempotencyKey);

    Page<MerchantVerifiedBoostSelection> findAllByMerchantOwnerUserId(Long ownerId, Pageable pageable);
}
