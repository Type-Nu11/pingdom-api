package com.typenull.pingdom.campaign.application;

import com.typenull.pingdom.campaign.api.dto.PopupCampaignPageResponse;
import com.typenull.pingdom.campaign.api.dto.PopupCampaignResponse;
import com.typenull.pingdom.campaign.domain.MerchantBrand;
import com.typenull.pingdom.campaign.domain.PopupCampaign;
import com.typenull.pingdom.campaign.domain.PopupCampaignStatus;
import com.typenull.pingdom.campaign.domain.exception.CampaignErrorCode;
import com.typenull.pingdom.campaign.domain.exception.CampaignException;
import com.typenull.pingdom.campaign.infrastructure.MerchantBrandRepository;
import com.typenull.pingdom.campaign.infrastructure.PopupCampaignRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PopupCampaignQueryService {

    private final PopupCampaignRepository campaignRepository;
    private final MerchantBrandRepository brandRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PopupCampaignPageResponse list(Long placeId, int page, int limit) {
        LocalDateTime now = LocalDateTime.now(clock);
        Page<PopupCampaign> result = campaignRepository.findDiscoverable(
                PopupCampaignStatus.PUBLISHED,
                now,
                placeId,
                PageRequest.of(
                        Math.max(page - 1, 0),
                        Math.min(Math.max(limit, 1), 100),
                        Sort.by(Sort.Order.asc("endsAt"), Sort.Order.desc("id"))
                )
        );
        Map<Long, MerchantBrand> brands = brandRepository.findAllById(
                        result.getContent().stream().map(PopupCampaign::getBrandId).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(MerchantBrand::getId, Function.identity()));
        return new PopupCampaignPageResponse(
                result.getContent().stream()
                        .map(campaign -> PopupCampaignResponse.from(campaign, brands.get(campaign.getBrandId())))
                        .toList(),
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public PopupCampaignResponse get(Long campaignId) {
        LocalDateTime now = LocalDateTime.now(clock);
        PopupCampaign campaign = campaignRepository
                .findByIdAndStatusAndStartsAtLessThanEqualAndEndsAtAfter(
                        campaignId,
                        PopupCampaignStatus.PUBLISHED,
                        now,
                        now
                )
                .orElseThrow(() -> new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
        MerchantBrand brand = brandRepository.findById(campaign.getBrandId())
                .orElseThrow(() -> new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
        return PopupCampaignResponse.from(campaign, brand);
    }
}
