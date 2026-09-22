package com.typenull.pingdom.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.campaign.api.dto.PopupCampaignCreateRequest;
import com.typenull.pingdom.campaign.api.dto.BrandCreateRequest;
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
import java.util.Optional;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantCampaignServiceTest {

    private static final Long OWNER_ID = 10L;
    private static final Long PLACE_ID = 100L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 12, 0);

    @Mock private MerchantBrandRepository brandRepository;
    @Mock private PopupCampaignRepository campaignRepository;
    @Mock private CampaignAccessPolicy accessPolicy;
    @Mock private Clock clock;

    @InjectMocks private MerchantCampaignService service;

    /**
     * 캠페인 생성·게시에서 사용할 현재 시각을 UTC로 고정한다.
     */
    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-08-01T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    /**
     * 소유 브랜드로 캠페인을 생성할 때 장소 소유권을 확인하고 DRAFT 상태와 브랜드 ID를 반환하는지 검증한다.
     */
    @Test
    void createsOwnedCampaignDraft() {
        MerchantBrand brand = brand(1L);
        when(brandRepository.findByIdAndMerchantOwnerUserId(1L, OWNER_ID)).thenReturn(Optional.of(brand));
        when(campaignRepository.save(org.mockito.ArgumentMatchers.any(PopupCampaign.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createCampaign(OWNER_ID, request());

        verify(accessPolicy).requireOwnedPlace(OWNER_ID, PLACE_ID, NOW);
        assertThat(response.status()).isEqualTo(PopupCampaignStatus.DRAFT);
        assertThat(response.brandId()).isEqualTo(1L);
    }

    /**
     * 소유자 조건의 브랜드 조회가 비어 있으면 BRAND_NOT_FOUND로 캠페인 생성을 거절하는지 검증한다.
     */
    @Test
    void hidesUnownedBrand() {
        when(brandRepository.findByIdAndMerchantOwnerUserId(1L, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createCampaign(OWNER_ID, request()))
                .isInstanceOfSatisfying(CampaignException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CampaignErrorCode.BRAND_NOT_FOUND));
    }

    /**
     * 저장된 초안을 게시할 때 현재 장소 소유권을 다시 확인하고 PUBLISHED로 전이되는지 검증한다.
     */
    @Test
    void publishingRevalidatesPlaceOwnership() {
        PopupCampaign campaign = PopupCampaign.draft(
                1L,
                OWNER_ID,
                PLACE_ID,
                "성수 팝업",
                "설명",
                NOW.minusHours(1),
                NOW.plusDays(1),
                NOW.minusDays(1)
        );
        MerchantBrand brand = brand(1L);
        when(campaignRepository.findOwnedByIdForUpdate(5L, OWNER_ID)).thenReturn(Optional.of(campaign));
        when(brandRepository.findByIdAndMerchantOwnerUserId(1L, OWNER_ID)).thenReturn(Optional.of(brand));

        var response = service.publish(OWNER_ID, 5L);

        verify(accessPolicy).requireOwnedPlace(OWNER_ID, PLACE_ID, NOW);
        assertThat(response.status()).isEqualTo(PopupCampaignStatus.PUBLISHED);
    }

    /**
     * 사전 중복 조회 후에도 브랜드 이름 고유 제약이 발생하면 BRAND_NAME_DUPLICATED로 변환되는지 검증한다.
     */
    @Test
    void mapsDuplicateBrandConstraint() {
        when(brandRepository.existsByMerchantOwnerUserIdAndName(OWNER_ID, "핑덤")).thenReturn(false);
        when(brandRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(MerchantBrand.class)))
                .thenThrow(constraintViolation("uq_merchant_brand_owner_name"));

        assertThatThrownBy(() -> service.createBrand(
                OWNER_ID,
                new BrandCreateRequest("핑덤", null, null)
        )).isInstanceOfSatisfying(CampaignException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CampaignErrorCode.BRAND_NAME_DUPLICATED));
    }

    /**
     * 브랜드 저장의 외래 키 위반은 이름 중복으로 오인하지 않고 원래 무결성 예외 객체를 전달하는지 검증한다.
     */
    @Test
    void preservesUnrelatedBrandConstraint() {
        DataIntegrityViolationException violation = constraintViolation("fk_merchant_brand_owner");
        when(brandRepository.existsByMerchantOwnerUserIdAndName(OWNER_ID, "핑덤")).thenReturn(false);
        when(brandRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(MerchantBrand.class)))
                .thenThrow(violation);

        assertThatThrownBy(() -> service.createBrand(
                OWNER_ID,
                new BrandCreateRequest("핑덤", null, null)
        )).isSameAs(violation);
    }

    /**
     * 소유 브랜드 1과 장소에 대해 한 시간 뒤 시작하는 유효한 캠페인 생성 요청을 제공한다.
     */
    private PopupCampaignCreateRequest request() {
        return new PopupCampaignCreateRequest(
                1L,
                PLACE_ID,
                "성수 팝업",
                "설명",
                NOW.plusHours(1),
                NOW.plusDays(7)
        );
    }

    /**
     * 주어진 브랜드 ID와 이름을 응답 매핑에 제공하는 브랜드 mock을 만든다.
     */
    private MerchantBrand brand(Long id) {
        MerchantBrand brand = mock(MerchantBrand.class);
        when(brand.getId()).thenReturn(id);
        when(brand.getName()).thenReturn("핑덤");
        return brand;
    }

    /**
     * 주어진 제약 이름을 가진 Hibernate 원인을 Spring 무결성 예외로 감싸 제약별 오류 변환을 재현한다.
     */
    private DataIntegrityViolationException constraintViolation(String constraintName) {
        return new DataIntegrityViolationException(
                "brand insert failed",
                new ConstraintViolationException(
                        "constraint violation",
                        new SQLException("constraint violation"),
                        "insert into merchant_brand",
                        constraintName
                )
        );
    }
}
