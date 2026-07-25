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

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-08-01T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void createsDraftForOwnedBrandAndPlace() {
        MerchantBrand brand = brand(1L);
        when(brandRepository.findByIdAndMerchantOwnerUserId(1L, OWNER_ID)).thenReturn(Optional.of(brand));
        when(campaignRepository.save(org.mockito.ArgumentMatchers.any(PopupCampaign.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createCampaign(OWNER_ID, request());

        verify(accessPolicy).requireOwnedPlace(OWNER_ID, PLACE_ID, NOW);
        assertThat(response.status()).isEqualTo(PopupCampaignStatus.DRAFT);
        assertThat(response.brandId()).isEqualTo(1L);
    }

    @Test
    void foreignBrandIsHiddenAsNotFound() {
        when(brandRepository.findByIdAndMerchantOwnerUserId(1L, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createCampaign(OWNER_ID, request()))
                .isInstanceOfSatisfying(CampaignException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(CampaignErrorCode.BRAND_NOT_FOUND));
    }

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

    @Test
    void duplicateBrandConstraintIsMappedToDomainError() {
        when(brandRepository.existsByMerchantOwnerUserIdAndName(OWNER_ID, "핑덤")).thenReturn(false);
        when(brandRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(MerchantBrand.class)))
                .thenThrow(constraintViolation("uq_merchant_brand_owner_name"));

        assertThatThrownBy(() -> service.createBrand(
                OWNER_ID,
                new BrandCreateRequest("핑덤", null, null)
        )).isInstanceOfSatisfying(CampaignException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CampaignErrorCode.BRAND_NAME_DUPLICATED));
    }

    @Test
    void unrelatedBrandConstraintIsNotHiddenAsDuplicate() {
        DataIntegrityViolationException violation = constraintViolation("fk_merchant_brand_owner");
        when(brandRepository.existsByMerchantOwnerUserIdAndName(OWNER_ID, "핑덤")).thenReturn(false);
        when(brandRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(MerchantBrand.class)))
                .thenThrow(violation);

        assertThatThrownBy(() -> service.createBrand(
                OWNER_ID,
                new BrandCreateRequest("핑덤", null, null)
        )).isSameAs(violation);
    }

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

    private MerchantBrand brand(Long id) {
        MerchantBrand brand = mock(MerchantBrand.class);
        when(brand.getId()).thenReturn(id);
        when(brand.getName()).thenReturn("핑덤");
        return brand;
    }

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
