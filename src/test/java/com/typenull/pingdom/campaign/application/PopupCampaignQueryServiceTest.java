package com.typenull.pingdom.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.campaign.domain.PopupCampaignStatus;
import com.typenull.pingdom.campaign.domain.exception.CampaignErrorCode;
import com.typenull.pingdom.campaign.domain.exception.CampaignException;
import com.typenull.pingdom.campaign.infrastructure.MerchantBrandRepository;
import com.typenull.pingdom.campaign.infrastructure.PopupCampaignRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PopupCampaignQueryServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 12, 0);

    @Mock private PopupCampaignRepository campaignRepository;
    @Mock private MerchantBrandRepository brandRepository;
    @Mock private Clock clock;

    @InjectMocks private PopupCampaignQueryService service;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-08-01T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void campaignRejectedByDiscoverabilityPolicyIsHiddenAsNotFound() {
        when(campaignRepository.findDiscoverableById(1L, PopupCampaignStatus.PUBLISHED, NOW))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(1L))
                .isInstanceOfSatisfying(CampaignException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CampaignErrorCode.CAMPAIGN_NOT_FOUND));

        verify(campaignRepository).findDiscoverableById(1L, PopupCampaignStatus.PUBLISHED, NOW);
    }
}
