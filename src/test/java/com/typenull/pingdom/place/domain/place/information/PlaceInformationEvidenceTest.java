package com.typenull.pingdom.place.domain.place.information;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceInformationEvidenceTest {

    /** 출처 참조·URL·설명이 모두 비어 있으면 근거 제출을 거부하는지 확인한다. */
    @Test
    void rejectsEmptyEvidencePayload() {
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

    /** 관리자 검증 시 ADMIN_VERIFIED와 검증자·사유·검토 및 갱신 시각을 함께 저장하는지 확인한다. */
    @Test
    void storesEvidenceReviewMetadata() {
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

    /** 사진 근거 반려 시 공백 사유를 거부하고 필수 사유 오류를 반환하는지 확인한다. */
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

    /** 근거를 연결할 위치·주소가 있는 장소를 메모리에 만든다. */
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
