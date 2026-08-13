package com.typenull.pingdom.identity.application.service.merchant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.repository.MerchantPlaceClaimAttachmentRepository;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantPlaceClaimAttachmentOrphanServiceTest {
    @Mock private MerchantPlaceClaimAttachmentRepository attachmentRepository;
    @Mock private S3ObjectStorage storage;
    @Mock private S3ObjectDeleteOutboxPublisher deletePublisher;
    private MerchantPlaceClaimAttachmentOrphanService service;

    @BeforeEach
    void setUp() {
        service = new MerchantPlaceClaimAttachmentOrphanService(
                attachmentRepository, storage, deletePublisher);
    }

    @Test
    void requestsDeletionOnlyForUnregisteredKeys() {
        when(attachmentRepository.findAll()).thenReturn(List.of());
        when(storage.listKeys("private/merchant-place-claims/", 10))
                .thenReturn(new S3ObjectStorage.S3ListResult(List.of("private/merchant-place-claims/orphan"), false));

        assertEquals(1, service.requestOrphanDeletion(10));

        verify(deletePublisher).publish(eq("private/merchant-place-claims/orphan"),
                eq("MERCHANT_PLACE_CLAIM_ATTACHMENT"), eq("ORPHAN"), eq("ORPHAN_CLEANUP"));
    }
}
