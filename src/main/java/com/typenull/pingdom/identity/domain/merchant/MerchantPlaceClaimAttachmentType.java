package com.typenull.pingdom.identity.domain.merchant;

public enum MerchantPlaceClaimAttachmentType {
    BUSINESS_LICENSE,
    RESIDENT_REGISTRATION,
    REPRESENTATIVE_IMAGE;

    public boolean isSensitive() {
        return this == BUSINESS_LICENSE || this == RESIDENT_REGISTRATION;
    }
}
