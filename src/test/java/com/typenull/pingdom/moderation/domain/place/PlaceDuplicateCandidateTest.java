package com.typenull.pingdom.moderation.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceDuplicateCandidateTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 23, 10, 0);

    /**
     * 장소 ID를 역순으로 탐지해도 작은 ID를 왼쪽에 저장하고 초기 판정 상태를 PENDING으로 두는지 검증.
     */
    @Test
    void normalizesPlacePairWhenDetectingCandidate() {
        PlaceDuplicateCandidate candidate = PlaceDuplicateCandidate.detect(
                20L,
                10L,
                PlaceDuplicateMatchReason.NAME_ADDRESS_COORDINATE,
                new BigDecimal("0.8750"),
                12,
                NOW
        );

        assertThat(candidate.getLeftPlaceId()).isEqualTo(10L);
        assertThat(candidate.getRightPlaceId()).isEqualTo(20L);
        assertThat(candidate.getStatus()).isEqualTo(PlaceDuplicateDecisionStatus.PENDING);
    }

    /**
     * 중복 후보를 확인한 뒤 병합 완료를 기록하면 MERGED 상태와 심사자·병합 이력 ID를 유지하는지 검증.
     */
    @Test
    void connectsConfirmedCandidateToMerge() {
        PlaceDuplicateCandidate candidate = candidate();

        candidate.confirm(7L, "동일 장소 확인", NOW.plusMinutes(1));
        candidate.markMerged(30L, NOW.plusMinutes(2));

        assertThat(candidate.getStatus()).isEqualTo(PlaceDuplicateDecisionStatus.MERGED);
        assertThat(candidate.getReviewedByAdminUserId()).isEqualTo(7L);
        assertThat(candidate.getMergeHistoryId()).isEqualTo(30L);
    }

    /**
     * 같은 장소끼리의 후보 생성은 거절하고 이미 기각된 후보는 다시 확인할 수 없는지 검증.
     */
    @Test
    void rejectsInvalidPairAndRepeatedDecision() {
        assertThatThrownBy(() -> PlaceDuplicateCandidate.detect(
                10L,
                10L,
                PlaceDuplicateMatchReason.KAKAO_PLACE_ID,
                BigDecimal.ONE,
                0,
                NOW
        )).isInstanceOf(IllegalArgumentException.class);

        PlaceDuplicateCandidate candidate = candidate();
        candidate.reject(7L, "서로 다른 장소", NOW.plusMinutes(1));

        assertThatThrownBy(() -> candidate.confirm(7L, "재판정", NOW.plusMinutes(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 판정 전이를 검증할 동일 카카오 장소 ID 기반의 대기 중복 후보를 생성.
     */
    private PlaceDuplicateCandidate candidate() {
        return PlaceDuplicateCandidate.detect(
                10L,
                20L,
                PlaceDuplicateMatchReason.KAKAO_PLACE_ID,
                BigDecimal.ONE,
                0,
                NOW
        );
    }
}
