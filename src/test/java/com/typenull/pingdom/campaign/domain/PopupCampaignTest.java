package com.typenull.pingdom.campaign.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PopupCampaignTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 12, 0);

    /**
     * 기간 내 초안을 게시한 뒤 종료하면 PUBLISHED에서 CLOSED로 전이되는지 검증.
     */
    @Test
    void publishesThenClosesDraft() {
        PopupCampaign campaign = draft();

        campaign.publish(NOW);
        assertThat(campaign.getStatus()).isEqualTo(PopupCampaignStatus.PUBLISHED);

        campaign.close(NOW.plusHours(1));
        assertThat(campaign.getStatus()).isEqualTo(PopupCampaignStatus.CLOSED);
    }

    /**
     * 시작과 종료 시각이 동일한 캠페인 생성이 IllegalArgumentException으로 거절되는지 검증.
     */
    @Test
    void invalidPeriodIsRejected() {
        assertThatThrownBy(() -> PopupCampaign.draft(
                1L, 10L, 100L, "팝업", "설명", NOW, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 게시 후 종료한 캠페인을 다시 게시하면 IllegalStateException이 발생하는지 검증.
     */
    @Test
    void rejectsClosedCampaignPublication() {
        PopupCampaign campaign = draft();
        campaign.publish(NOW);
        campaign.close(NOW.plusHours(1));

        assertThatThrownBy(() -> campaign.publish(NOW.plusHours(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 게시 중인 캠페인의 브랜드·장소·기간 등을 수정하면 IllegalStateException이 발생하는지 검증.
     */
    @Test
    void publishedCampaignCannotBeUpdated() {
        PopupCampaign campaign = draft();
        campaign.publish(NOW);

        assertThatThrownBy(() -> campaign.update(
                2L,
                200L,
                "변경",
                "변경 설명",
                NOW,
                NOW.plusDays(1),
                NOW
        )).isInstanceOf(IllegalStateException.class);
    }

    /**
     * 고정된 현재 시각이 행사 기간 안에 포함되는 7일 팝업 캠페인 초안을 생성.
     */
    private PopupCampaign draft() {
        return PopupCampaign.draft(
                1L,
                10L,
                100L,
                "성수 팝업",
                "브랜드 팝업 캠페인",
                NOW.minusHours(1),
                NOW.plusDays(7),
                NOW.minusDays(1)
        );
    }
}
