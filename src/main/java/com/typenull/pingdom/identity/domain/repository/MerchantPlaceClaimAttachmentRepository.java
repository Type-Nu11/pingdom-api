package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimAttachment;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimAttachmentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantPlaceClaimAttachmentRepository extends JpaRepository<MerchantPlaceClaimAttachment, Long> {

    List<MerchantPlaceClaimAttachment> findAllByClaimIdOrderByDocumentTypeAscDisplayOrderAscIdAsc(Long claimId);

    List<MerchantPlaceClaimAttachment> findAllByClaimIdAndDocumentTypeOrderByDisplayOrderAscIdAsc(
            Long claimId, MerchantPlaceClaimAttachmentType documentType);

    Optional<MerchantPlaceClaimAttachment> findByIdAndClaimId(Long id, Long claimId);

    @Query("select attachment from MerchantPlaceClaimAttachment attachment "
            + "where attachment.claimId in (select claim.id from MerchantPlaceClaim claim "
            + "where claim.merchantOwnerUserId = :userId)")
    List<MerchantPlaceClaimAttachment> findAllByClaimOwnerUserId(@Param("userId") Long userId);
}
