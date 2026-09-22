package com.typenull.pingdom.place.api.dto.place.detail;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInformation;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceVisitDecisionMerchantInformationResponseTest {

    /** 사업자 정보의 설명·연락처·웹사이트·예약 URL·갱신 시각이 방문 판단 응답에 반영되는지 확인한다. 편집자 필드의 직렬화는 검사하지 않는다. */
    @Test
    void mapsMerchantVisitInformation() {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 5, 10, 0);
        MerchantPlaceInformation information = MerchantPlaceInformation.create(
                1L,
                "관광객을 위한 장소 소개",
                "010-1234-5678",
                "https://pingdom.test/place",
                "https://pingdom.test/reservations",
                99L,
                updatedAt
        );

        PlaceVisitDecisionMerchantInformationResponse response =
                PlaceVisitDecisionMerchantInformationResponse.from(information);

        assertThat(response.description()).isEqualTo("관광객을 위한 장소 소개");
        assertThat(response.contactPhone()).isEqualTo("010-1234-5678");
        assertThat(response.websiteUrl()).isEqualTo("https://pingdom.test/place");
        assertThat(response.reservationUrl()).isEqualTo("https://pingdom.test/reservations");
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }
}
