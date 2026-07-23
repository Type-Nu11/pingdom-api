package com.typenull.pingdom.moderation.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceDuplicateCandidateTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 23, 10, 0);

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

    @Test
    void confirmsThenConnectsCandidateToMergeHistory() {
        PlaceDuplicateCandidate candidate = candidate();

        candidate.confirm(7L, "동일 장소 확인", NOW.plusMinutes(1));
        candidate.markMerged(30L, NOW.plusMinutes(2));

        assertThat(candidate.getStatus()).isEqualTo(PlaceDuplicateDecisionStatus.MERGED);
        assertThat(candidate.getReviewedByAdminUserId()).isEqualTo(7L);
        assertThat(candidate.getMergeHistoryId()).isEqualTo(30L);
    }

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
