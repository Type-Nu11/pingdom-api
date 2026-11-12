package com.typenull.pingdom.place.application.service.registration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitation;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMember;
import com.typenull.pingdom.offer.domain.TouristOffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 기존 심사 이력 JSON 형식을 유지하며 변경 전에 취득한 값만 직렬화합니다. */
@Component
@RequiredArgsConstructor
public class MerchantPlaceApplicationReviewSnapshotSerializer {
    private final ObjectMapper objectMapper;

    public ReviewSnapshots capture(
            Long placeId, Long expectedOwnerUserId, Long actualOwnerUserId,
            List<MerchantPlaceMember> members, List<MerchantPlaceInvitation> invitations,
            List<TouristOffer> currentOffers
    ) {
        List<Map<String, Object>> teamMembers = members.stream()
                .map(member -> snapshotMap(
                        "id", member.getId(),
                        "userId", member.getUserId(),
                        "role", member.getRole(),
                        "status", member.getStatus(),
                        "invitedBy", member.getInvitedBy()
                ))
                .toList();
        List<Map<String, Object>> pendingInvitations = invitations
                .stream()
                .map(invitation -> snapshotMap(
                        "id", invitation.getId(),
                        "inviteeUserId", invitation.getInviteeUserId(),
                        "role", invitation.getRole(),
                        "invitedBy", invitation.getInvitedBy(),
                        "expiresAt", invitation.getExpiresAt()
                ))
                .toList();
        List<Map<String, Object>> offers = currentOffers
                .stream()
                .map(this::offerSnapshot)
                .toList();
        return new ReviewSnapshots(
                serializeSnapshot(snapshotMap(
                        "placeId", placeId,
                        "expectedPreviousOwnerUserId", expectedOwnerUserId,
                        "actualPreviousOwnerUserId", actualOwnerUserId
                )),
                serializeSnapshot(snapshotMap(
                        "members", teamMembers,
                        "pendingInvitations", pendingInvitations
                )),
                serializeSnapshot(snapshotMap("offers", offers))
        );
    }

    private Map<String, Object> offerSnapshot(TouristOffer offer) {
        return snapshotMap(
                "id", offer.getId(),
                "status", offer.getStatus(),
                "issuedQuantity", offer.getIssuedQuantity(),
                "startsAt", offer.getStartsAt(),
                "endsAt", offer.getEndsAt()
        );
    }

    private String serializeSnapshot(Object snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("심사 영향 스냅샷 직렬화에 실패했습니다.", exception);
        }
    }

    private Map<String, Object> snapshotMap(Object... entries) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            snapshot.put((String) entries[index], entries[index + 1]);
        }
        return snapshot;
    }

    public record ReviewSnapshots(String previousOwner, String team, String offers) {
        public static ReviewSnapshots empty() {
            return new ReviewSnapshots("{}", "{}", "{}");
        }
    }
}
