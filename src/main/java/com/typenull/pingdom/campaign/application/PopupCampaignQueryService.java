package com.typenull.pingdom.campaign.application;

import com.typenull.pingdom.campaign.api.dto.PublicPopupCampaignPageResponse;
import com.typenull.pingdom.campaign.api.dto.PublicPopupCampaignResponse;
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

/**
 * 공개 기간과 현재 점주 자격을 만족하는 캠페인에 브랜드 정보를 묶어 반환합니다.
 * 페이지는 1~10000, 크기는 1~100으로 보정하고 종료가 가까운 순서와 ID 역순으로 정렬합니다.
 */
@Service
@RequiredArgsConstructor
public class PopupCampaignQueryService {

    private static final int MAX_PAGE = 10_000;

    private final PopupCampaignRepository campaignRepository;
    private final MerchantBrandRepository brandRepository;
    private final Clock clock;

    /**
     * 현재 공개 기간과 점주 자격·소유 관계를 만족하는 캠페인만 선택 장소 조건으로 조회해 브랜드 정보를 결합합니다.
     * 페이지는 1~10000, 크기는 1~100으로 보정하고 종료 시각 오름차순·ID 내림차순의 공개 목록을 반환합니다.
     */
    @Transactional(readOnly = true)
    public PublicPopupCampaignPageResponse list(Long placeId, int page, int limit) {
        LocalDateTime now = LocalDateTime.now(clock);
        int safePage = Math.max(1, Math.min(page, MAX_PAGE));
        int safeLimit = Math.max(1, Math.min(limit, 100));
        Page<PopupCampaign> result = campaignRepository.findDiscoverable(
                PopupCampaignStatus.PUBLISHED,
                now,
                placeId,
                PageRequest.of(
                        safePage - 1,
                        safeLimit,
                        Sort.by(Sort.Order.asc("endsAt"), Sort.Order.desc("id"))
                )
        );
        Map<Long, MerchantBrand> brands = brandRepository.findAllById(
                        result.getContent().stream().map(PopupCampaign::getBrandId).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(MerchantBrand::getId, Function.identity()));
        return new PublicPopupCampaignPageResponse(
                result.getContent().stream()
                        .map(campaign -> PublicPopupCampaignResponse.from(campaign, brands.get(campaign.getBrandId())))
                        .toList(),
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    /**
     * 현재 공개 기간과 점주 자격·소유 관계를 만족하는 캠페인을 브랜드 정보와 함께 반환합니다.
     * 노출 불가 캠페인이나 연결 브랜드 부재는 모두 CAMPAIGN_NOT_FOUND로 처리합니다.
     */
    @Transactional(readOnly = true)
    public PublicPopupCampaignResponse get(Long campaignId) {
        LocalDateTime now = LocalDateTime.now(clock);
        PopupCampaign campaign = campaignRepository
                .findDiscoverableById(
                        campaignId,
                        PopupCampaignStatus.PUBLISHED,
                        now
                )
                .orElseThrow(() -> new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
        MerchantBrand brand = brandRepository.findById(campaign.getBrandId())
                .orElseThrow(() -> new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
        return PublicPopupCampaignResponse.from(campaign, brand);
    }
}
