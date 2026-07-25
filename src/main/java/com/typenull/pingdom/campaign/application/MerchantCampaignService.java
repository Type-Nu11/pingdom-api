package com.typenull.pingdom.campaign.application;

import com.typenull.pingdom.campaign.api.dto.BrandCreateRequest;
import com.typenull.pingdom.campaign.api.dto.BrandPageResponse;
import com.typenull.pingdom.campaign.api.dto.BrandResponse;
import com.typenull.pingdom.campaign.api.dto.PopupCampaignCreateRequest;
import com.typenull.pingdom.campaign.api.dto.PopupCampaignPageResponse;
import com.typenull.pingdom.campaign.api.dto.PopupCampaignResponse;
import com.typenull.pingdom.campaign.domain.MerchantBrand;
import com.typenull.pingdom.campaign.domain.PopupCampaign;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantCampaignService {

    private final MerchantBrandRepository brandRepository;
    private final PopupCampaignRepository campaignRepository;
    private final CampaignAccessPolicy accessPolicy;
    private final Clock clock;

    @Transactional
    public BrandResponse createBrand(Long ownerId, BrandCreateRequest request) {
        LocalDateTime now = now();
        accessPolicy.requireActiveOwner(ownerId, now);
        String normalizedName = request.name().trim();
        if (brandRepository.existsByMerchantOwnerUserIdAndName(ownerId, normalizedName)) {
            throw new CampaignException(CampaignErrorCode.BRAND_NAME_DUPLICATED);
        }
        try {
            return BrandResponse.from(brandRepository.saveAndFlush(MerchantBrand.create(
                    ownerId,
                    normalizedName,
                    request.description(),
                    request.logoUrl(),
                    now
            )));
        } catch (DataIntegrityViolationException exception) {
            throw new CampaignException(CampaignErrorCode.BRAND_NAME_DUPLICATED);
        } catch (IllegalArgumentException exception) {
            throw new CampaignException(CampaignErrorCode.INVALID_INPUT);
        }
    }

    @Transactional(readOnly = true)
    public BrandPageResponse listBrands(Long ownerId, int page, int limit) {
        Page<MerchantBrand> result = brandRepository.findAllByMerchantOwnerUserId(
                ownerId,
                pageRequest(page, limit, "createdAt")
        );
        return new BrandPageResponse(
                result.getContent().stream().map(BrandResponse::from).toList(),
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional
    public BrandResponse updateBrand(Long ownerId, Long brandId, BrandCreateRequest request) {
        LocalDateTime now = now();
        accessPolicy.requireActiveOwner(ownerId, now);
        MerchantBrand brand = findOwnedBrand(ownerId, brandId);
        String normalizedName = request.name().trim();
        if (!brand.getName().equals(normalizedName)
                && brandRepository.existsByMerchantOwnerUserIdAndName(ownerId, normalizedName)) {
            throw new CampaignException(CampaignErrorCode.BRAND_NAME_DUPLICATED);
        }
        try {
            brand.update(normalizedName, request.description(), request.logoUrl(), now);
            return BrandResponse.from(brand);
        } catch (IllegalArgumentException exception) {
            throw new CampaignException(CampaignErrorCode.INVALID_INPUT);
        }
    }

    @Transactional
    public PopupCampaignResponse createCampaign(Long ownerId, PopupCampaignCreateRequest request) {
        LocalDateTime now = now();
        accessPolicy.requireOwnedPlace(ownerId, request.placeId(), now);
        MerchantBrand brand = findOwnedBrand(ownerId, request.brandId());
        if (!request.endsAt().isAfter(request.startsAt()) || !request.endsAt().isAfter(now)) {
            throw new CampaignException(CampaignErrorCode.INVALID_PERIOD);
        }
        try {
            PopupCampaign campaign = campaignRepository.save(PopupCampaign.draft(
                    brand.getId(),
                    ownerId,
                    request.placeId(),
                    request.title(),
                    request.description(),
                    request.startsAt(),
                    request.endsAt(),
                    now
            ));
            return PopupCampaignResponse.from(campaign, brand);
        } catch (IllegalArgumentException exception) {
            throw new CampaignException(CampaignErrorCode.INVALID_INPUT);
        }
    }

    @Transactional(readOnly = true)
    public PopupCampaignPageResponse listCampaigns(Long ownerId, int page, int limit) {
        Page<PopupCampaign> result = campaignRepository.findAllByMerchantOwnerUserId(
                ownerId,
                pageRequest(page, limit, "createdAt")
        );
        Map<Long, MerchantBrand> brands = brandMap(result);
        return pageResponse(result, brands);
    }

    @Transactional
    public PopupCampaignResponse updateCampaign(
            Long ownerId,
            Long campaignId,
            PopupCampaignCreateRequest request
    ) {
        LocalDateTime now = now();
        PopupCampaign campaign = findOwnedCampaign(ownerId, campaignId);
        accessPolicy.requireOwnedPlace(ownerId, request.placeId(), now);
        MerchantBrand brand = findOwnedBrand(ownerId, request.brandId());
        if (!request.endsAt().isAfter(request.startsAt()) || !request.endsAt().isAfter(now)) {
            throw new CampaignException(CampaignErrorCode.INVALID_PERIOD);
        }
        try {
            campaign.update(
                    brand.getId(),
                    request.placeId(),
                    request.title(),
                    request.description(),
                    request.startsAt(),
                    request.endsAt(),
                    now
            );
            return PopupCampaignResponse.from(campaign, brand);
        } catch (IllegalStateException exception) {
            throw new CampaignException(CampaignErrorCode.INVALID_STATE);
        } catch (IllegalArgumentException exception) {
            throw new CampaignException(CampaignErrorCode.INVALID_INPUT);
        }
    }

    @Transactional
    public PopupCampaignResponse publish(Long ownerId, Long campaignId) {
        LocalDateTime now = now();
        PopupCampaign campaign = findOwnedCampaign(ownerId, campaignId);
        accessPolicy.requireOwnedPlace(ownerId, campaign.getPlaceId(), now);
        try {
            campaign.publish(now);
        } catch (IllegalStateException exception) {
            throw new CampaignException(CampaignErrorCode.INVALID_STATE);
        }
        return PopupCampaignResponse.from(campaign, findOwnedBrand(ownerId, campaign.getBrandId()));
    }

    @Transactional
    public PopupCampaignResponse close(Long ownerId, Long campaignId) {
        LocalDateTime now = now();
        PopupCampaign campaign = findOwnedCampaign(ownerId, campaignId);
        accessPolicy.requireOwnedPlace(ownerId, campaign.getPlaceId(), now);
        try {
            campaign.close(now);
        } catch (IllegalStateException exception) {
            throw new CampaignException(CampaignErrorCode.INVALID_STATE);
        }
        return PopupCampaignResponse.from(campaign, findOwnedBrand(ownerId, campaign.getBrandId()));
    }

    private MerchantBrand findOwnedBrand(Long ownerId, Long brandId) {
        return brandRepository.findByIdAndMerchantOwnerUserId(brandId, ownerId)
                .orElseThrow(() -> new CampaignException(CampaignErrorCode.BRAND_NOT_FOUND));
    }

    private PopupCampaign findOwnedCampaign(Long ownerId, Long campaignId) {
        return campaignRepository.findOwnedByIdForUpdate(campaignId, ownerId)
                .orElseThrow(() -> new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
    }

    private Map<Long, MerchantBrand> brandMap(Page<PopupCampaign> campaigns) {
        var ids = campaigns.getContent().stream().map(PopupCampaign::getBrandId).collect(Collectors.toSet());
        return brandRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(MerchantBrand::getId, Function.identity()));
    }

    private PopupCampaignPageResponse pageResponse(Page<PopupCampaign> result, Map<Long, MerchantBrand> brands) {
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

    private PageRequest pageRequest(int page, int limit, String property) {
        return PageRequest.of(
                Math.max(page - 1, 0),
                Math.min(Math.max(limit, 1), 100),
                Sort.by(Sort.Order.desc(property), Sort.Order.desc("id"))
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
