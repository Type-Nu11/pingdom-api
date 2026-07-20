package com.typenull.pingdom.place.domain.place.information;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceInformationEvidenceTest {

    @Test
    void submitRequiresAtLeastOneEvidencePayload() {
        MapPlace place = place();

        assertThatThrownBy(() -> PlaceInformationEvidence.submit(
                place,
                PlaceInformationSourceType.MERCHANT_OWNER,
                PlaceInformationEvidenceType.DOCUMENT,
                " ",
                null,
                "",
                1L,
                LocalDateTime.now()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("evidence payload must not be empty");
    }

    @Test
    void verifyByAdminStoresReviewerMetadata() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 7, 20, 10, 0);
        LocalDateTime reviewedAt = submittedAt.plusHours(2);
        PlaceInformationEvidence evidence = PlaceInformationEvidence.submit(
                place(),
                PlaceInformationSourceType.MERCHANT_OWNER,
                PlaceInformationEvidenceType.BUSINESS_CLAIM,
                "claim-1",
                null,
                "사업자 소유권 증빙",
                10L,
                submittedAt
        );

        evidence.verifyByAdmin(99L, "증빙 확인", reviewedAt);

        assertThat(evidence.getVerificationStatus()).isEqualTo(PlaceInformationVerificationStatus.ADMIN_VERIFIED);
        assertThat(evidence.getReviewedByAdminUserId()).isEqualTo(99L);
        assertThat(evidence.getReviewReason()).isEqualTo("증빙 확인");
        assertThat(evidence.getReviewedAt()).isEqualTo(reviewedAt);
        assertThat(evidence.getUpdatedAt()).isEqualTo(reviewedAt);
    }

    @Test
    void rejectRequiresReviewReason() {
        PlaceInformationEvidence evidence = PlaceInformationEvidence.submit(
                place(),
                PlaceInformationSourceType.USER_REPORT,
                PlaceInformationEvidenceType.PHOTO,
                null,
                "https://example.com/evidence.jpg",
                null,
                10L,
                LocalDateTime.now()
        );

        assertThatThrownBy(() -> evidence.reject(99L, " ", LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reviewReason must not be blank");
    }

    private MapPlace place() {
        return MapPlace.builder()
                .name("증빙 장소")
                .address("경상남도 진주시 증빙로 1")
                .latitude(35.1801)
                .longitude(128.1078)
                .registrant("tester")
                .build();
    }
}
