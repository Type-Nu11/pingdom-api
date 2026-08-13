package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimAttachment;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceClaimAttachmentRepository;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantPlaceClaimAttachmentOrphanService {
    private static final String PREFIX = "private/merchant-place-claims/";
    private final MerchantPlaceClaimAttachmentRepository attachmentRepository;
    private final S3ObjectStorage storage;
    private final S3ObjectDeleteOutboxPublisher deletePublisher;

    @Transactional(readOnly = true)
    public int requestOrphanDeletion(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 10_000));
        Set<String> registeredKeys = attachmentRepository.findAll().stream()
                .map(MerchantPlaceClaimAttachment::getStorageKey)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        S3ObjectStorage.S3ListResult result = storage.listKeys(PREFIX, safeLimit);
        int requested = 0;
        for (String key : result.keys()) {
            if (!registeredKeys.contains(key) && requested < safeLimit) {
                deletePublisher.publish(key, "MERCHANT_PLACE_CLAIM_ATTACHMENT", "ORPHAN", "ORPHAN_CLEANUP");
                requested++;
            }
        }
        return requested;
    }
}
