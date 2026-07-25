package com.typenull.pingdom.campaign.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PopupCampaignTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 12, 0);

    @Test
    void draftCanBePublishedAndClosed() {
        PopupCampaign campaign = draft();

        campaign.publish(NOW);
        assertThat(campaign.getStatus()).isEqualTo(PopupCampaignStatus.PUBLISHED);

        campaign.close(NOW.plusHours(1));
        assertThat(campaign.getStatus()).isEqualTo(PopupCampaignStatus.CLOSED);
    }

    @Test
    void invalidPeriodIsRejected() {
        assertThatThrownBy(() -> PopupCampaign.draft(
                1L, 10L, 100L, "팝업", "설명", NOW, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void closedCampaignCannotBePublishedAgain() {
        PopupCampaign campaign = draft();
        campaign.publish(NOW);
        campaign.close(NOW.plusHours(1));

        assertThatThrownBy(() -> campaign.publish(NOW.plusHours(2)))
                .isInstanceOf(IllegalStateException.class);
    }

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
