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

/**
 * 점주 소유 브랜드와 장소 팝업 캠페인의 작성·공개·종료를 연결합니다.
 * 브랜드/캠페인 수정은 해당 행을 잠그며 브랜드 이름 중복은 사전 조회와 특정 DB 유일 제약 오류로 구분합니다.
 */
@Service
@RequiredArgsConstructor
public class MerchantCampaignService {

    private final MerchantBrandRepository brandRepository;
    private final PopupCampaignRepository campaignRepository;
    private final CampaignAccessPolicy accessPolicy;
    private final Clock clock;

    /**
     * 활성 점주 자격을 확인하고 앞뒤 공백을 제거한 이름으로 브랜드를 저장·flush해 반환합니다.
     * 점주별 이름 중복은 사전 조회와 해당 고유 제약 오류에서 동일하게 거절하고 도메인 입력 오류도 변환합니다.
     */
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
            if (hasConstraint(exception, "uq_merchant_brand_owner_name")) {
                throw new CampaignException(CampaignErrorCode.BRAND_NAME_DUPLICATED);
            }
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new CampaignException(CampaignErrorCode.INVALID_INPUT);
        }
    }

    /**
     * 지정 점주가 소유한 브랜드를 생성 시각·ID 내림차순의 페이지로 반환합니다.
     * 페이지는 최소 1, 크기는 1~100으로 보정하고 전체 건수와 다음 페이지 여부를 함께 제공합니다.
     */
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

    /**
     * 활성 점주의 소유 브랜드를 잠가 이름·설명·로고를 갱신하고 flush한 결과를 반환합니다.
     * 브랜드 부재와 변경 이름 중복을 거절하며 해당 고유 제약 충돌·도메인 입력 오류를 업무 오류로 변환합니다.
     */
    @Transactional
    public BrandResponse updateBrand(Long ownerId, Long brandId, BrandCreateRequest request) {
        LocalDateTime now = now();
        accessPolicy.requireActiveOwner(ownerId, now);
        MerchantBrand brand = brandRepository.findOwnedByIdForUpdate(brandId, ownerId)
                .orElseThrow(() -> new CampaignException(CampaignErrorCode.BRAND_NOT_FOUND));
        String normalizedName = request.name().trim();
        if (!brand.getName().equals(normalizedName)
                && brandRepository.existsByMerchantOwnerUserIdAndName(ownerId, normalizedName)) {
            throw new CampaignException(CampaignErrorCode.BRAND_NAME_DUPLICATED);
        }
        try {
            brand.update(normalizedName, request.description(), request.logoUrl(), now);
            return BrandResponse.from(brandRepository.saveAndFlush(brand));
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_merchant_brand_owner_name")) {
                throw new CampaignException(CampaignErrorCode.BRAND_NAME_DUPLICATED);
            }
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new CampaignException(CampaignErrorCode.INVALID_INPUT);
        }
    }

    /**
     * 현재 소유 장소와 본인 브랜드를 확인해 팝업 캠페인 초안을 저장하고 브랜드를 포함한 응답을 반환합니다.
     * 종료가 시작 및 현재 시각 이후여야 하며 잘못된 기간·입력은 거절합니다.
     */
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

    /**
     * 요청한 새 장소와 브랜드가 점주 소유인지 확인하고 초안 캠페인만 변경합니다.
     * 종료는 시작 이후이자 현재 이후여야 하며, 변경 도중 도메인 검증 실패는 현재 트랜잭션을 롤백합니다.
     */
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

    /**
     * 본인 캠페인을 잠그고 현재 장소 소유 자격을 재확인해 종료 전 초안을 공개 상태로 바꿉니다.
     * 대상·브랜드 부재와 불가능한 상태 전이는 거절하고 브랜드 정보가 포함된 변경 응답을 반환합니다.
     */
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

    /**
     * 본인 캠페인을 잠그고 현재 장소 소유 자격을 재확인해 공개 캠페인을 종료합니다.
     * 공개 상태가 아닌 캠페인은 상태 오류로 거절하고 브랜드 정보가 포함된 변경 응답을 반환합니다.
     */
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

    private boolean hasConstraint(Throwable throwable, String constraintName) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
