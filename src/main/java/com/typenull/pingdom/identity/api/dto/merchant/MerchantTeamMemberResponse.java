package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMember;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberStatus;
import java.time.LocalDateTime;

public record MerchantTeamMemberResponse(Long id, Long placeId, Long userId, MerchantPlaceMemberRole role,
                                         MerchantPlaceMemberStatus status, LocalDateTime createdAt,
                                         LocalDateTime updatedAt) {
    public static MerchantTeamMemberResponse from(MerchantPlaceMember member) {
        return new MerchantTeamMemberResponse(member.getId(), member.getPlaceId(), member.getUserId(),
                member.getRole(), member.getStatus(), member.getCreatedAt(), member.getUpdatedAt());
    }
}
