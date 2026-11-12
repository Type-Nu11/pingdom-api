package com.typenull.pingdom.place.application.service.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitation;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMember;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
import com.typenull.pingdom.offer.domain.TouristOffer;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MerchantPlaceApplicationReviewSnapshotSerializerTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 24, 0, 0);

    @Test
    void preservesFieldsNullsEnumsAndConfiguredDatesBeforeMutation() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var serializer = new MerchantPlaceApplicationReviewSnapshotSerializer(mapper);
        MerchantPlaceMember member = MerchantPlaceMember.owner(30L, 20L, NOW);
        MerchantPlaceInvitation invitation = MerchantPlaceInvitation.pending(
                30L, 21L, 20L, MerchantPlaceMemberRole.STAFF, NOW.plusDays(1), NOW);
        TouristOffer offer = TouristOffer.draft(20L, 30L, "혜택", "소개", "할인", NOW, NOW.plusDays(1), 10, 1, NOW);
        offer.publish(NOW);

        var snapshots = serializer.capture(30L, null, 20L, List.of(member), List.of(invitation), List.of(offer));
        member.revoke(NOW.plusMinutes(1));
        invitation.revoke(NOW.plusMinutes(1));
        offer.close(NOW.plusMinutes(1));

        assertThat(snapshots.previousOwner()).isEqualTo(
                "{\"placeId\":30,\"expectedPreviousOwnerUserId\":null,\"actualPreviousOwnerUserId\":20}");
        assertThat(snapshots.team()).isEqualTo("{\"members\":[{\"id\":null,\"userId\":20,\"role\":\"OWNER\","
                + "\"status\":\"ACTIVE\",\"invitedBy\":20}],\"pendingInvitations\":[{\"id\":null,\"inviteeUserId\":21,"
                + "\"role\":\"STAFF\",\"invitedBy\":20,\"expiresAt\":\"2026-08-25T00:00:00\"}]}");
        assertThat(mapper.readTree(snapshots.offers()).path("offers").get(0).path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(mapper.readTree(snapshots.offers()).path("offers").get(0).path("startsAt").asText())
                .isEqualTo("2026-08-24T00:00:00");
        assertThat(mapper.readTree(snapshots.offers()).path("offers").get(0).path("issuedQuantity").asInt()).isZero();
    }

    @Test
    void propagatesSerializationFailureWithOriginalCause() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        JsonProcessingException cause = new JsonProcessingException("invalid snapshot") { };
        when(mapper.writeValueAsString(any())).thenThrow(cause);

        assertThatThrownBy(() -> new MerchantPlaceApplicationReviewSnapshotSerializer(mapper)
                .capture(30L, null, null, List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("심사 영향 스냅샷 직렬화에 실패했습니다.")
                .hasCause(cause);
    }

    @Test
    void keepsEmptyNewPlaceSnapshots() {
        var snapshots = MerchantPlaceApplicationReviewSnapshotSerializer.ReviewSnapshots.empty();
        assertThat(snapshots.previousOwner()).isEqualTo("{}");
        assertThat(snapshots.team()).isEqualTo("{}");
        assertThat(snapshots.offers()).isEqualTo("{}");
    }
}
