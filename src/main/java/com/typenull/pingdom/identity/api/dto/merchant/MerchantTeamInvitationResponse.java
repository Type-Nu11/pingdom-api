package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitation;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitationStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
import java.time.LocalDateTime;

public record MerchantTeamInvitationResponse(Long id, Long placeId, Long inviteeUserId,
                                             MerchantPlaceMemberRole role, MerchantPlaceInvitationStatus status,
                                             LocalDateTime expiresAt, LocalDateTime createdAt) {
    public static MerchantTeamInvitationResponse from(MerchantPlaceInvitation invitation) {
        return new MerchantTeamInvitationResponse(invitation.getId(), invitation.getPlaceId(), invitation.getInviteeUserId(),
                invitation.getRole(), invitation.getStatus(), invitation.getExpiresAt(), invitation.getCreatedAt());
    }
}
