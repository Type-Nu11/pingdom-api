package com.typenull.pingdom.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.campaign.domain.MerchantBrand;
import com.typenull.pingdom.campaign.domain.PopupCampaign;
import com.typenull.pingdom.campaign.domain.PopupCampaignStatus;
import com.typenull.pingdom.campaign.domain.exception.CampaignErrorCode;
import com.typenull.pingdom.campaign.domain.exception.CampaignException;
import com.typenull.pingdom.campaign.infrastructure.MerchantBrandRepository;
import com.typenull.pingdom.campaign.infrastructure.PopupCampaignRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    @Test
    void normalizesPublicCampaignPaginationToThePublishedContract() {
        when(campaignRepository.findDiscoverable(any(), any(), any(), any()))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(3)));
        when(brandRepository.findAllById(any())).thenReturn(List.of());

        var response = service.list(null, Integer.MIN_VALUE, Integer.MAX_VALUE);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(campaignRepository).findDiscoverable(
                org.mockito.ArgumentMatchers.eq(PopupCampaignStatus.PUBLISHED),
                org.mockito.ArgumentMatchers.eq(NOW),
                org.mockito.ArgumentMatchers.isNull(),
                pageableCaptor.capture()
        );

        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.limit()).isEqualTo(100);
        assertThat(response.totalCount()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void publicCampaignResponseSerializesDomainTimesAsUtcOffsets() {
        PopupCampaign campaign = PopupCampaign.draft(
                10L,
                20L,
                30L,
                "진주 여름 팝업",
                "남강변 팝업입니다.",
                NOW.minusDays(1),
                NOW.plusDays(1),
                NOW.minusDays(2)
        );
        campaign.publish(NOW);
        MerchantBrand brand = MerchantBrand.create(20L, "진주문화재단", null, null, NOW.minusDays(2));

        when(campaignRepository.findDiscoverableById(1L, PopupCampaignStatus.PUBLISHED, NOW))
                .thenReturn(Optional.of(campaign));
        when(brandRepository.findById(10L)).thenReturn(Optional.of(brand));

        var response = service.get(1L);

        assertThat(response.startsAt()).isEqualTo(NOW.minusDays(1).atOffset(ZoneOffset.UTC));
        assertThat(response.endsAt()).isEqualTo(NOW.plusDays(1).atOffset(ZoneOffset.UTC));
        assertThat(response.createdAt()).isEqualTo(NOW.minusDays(2).atOffset(ZoneOffset.UTC));
        assertThat(response.status()).isEqualTo(PopupCampaignStatus.PUBLISHED.name());
        assertThat(response.brandLogoUrl()).isNull();
    }
}
