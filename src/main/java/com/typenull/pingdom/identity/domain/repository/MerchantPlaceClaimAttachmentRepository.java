package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimAttachment;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimAttachmentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantPlaceClaimAttachmentRepository extends JpaRepository<MerchantPlaceClaimAttachment, Long> {

    List<MerchantPlaceClaimAttachment> findAllByClaimIdOrderByDocumentTypeAscDisplayOrderAscIdAsc(Long claimId);

    List<MerchantPlaceClaimAttachment> findAllByClaimIdAndDocumentTypeOrderByDisplayOrderAscIdAsc(
            Long claimId, MerchantPlaceClaimAttachmentType documentType);

    Optional<MerchantPlaceClaimAttachment> findByIdAndClaimId(Long id, Long claimId);
}
