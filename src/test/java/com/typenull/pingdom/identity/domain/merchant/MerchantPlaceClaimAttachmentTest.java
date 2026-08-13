package com.typenull.pingdom.identity.domain.merchant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MerchantPlaceClaimAttachmentTest {
    @Test
    void representativeImageCanChangeOrder() {
        LocalDateTime now = LocalDateTime.now();
        MerchantPlaceClaimAttachment attachment = MerchantPlaceClaimAttachment.create(
                1L, MerchantPlaceClaimAttachmentType.REPRESENTATIVE_IMAGE, "private/key",
                "image.jpg", "image/jpeg", 10L, "hash", 0, now);

        attachment.changeDisplayOrder(2, now.plusSeconds(1));

        assertEquals(2, attachment.getDisplayOrder());
        assertTrue(attachment.isRepresentativeImage());
    }
}
