package com.typenull.pingdom.menu.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceCapability;
import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceCapabilityPolicy;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.menu.api.dto.*;
import com.typenull.pingdom.menu.domain.*;
import com.typenull.pingdom.menu.domain.exception.*;
import com.typenull.pingdom.menu.infrastructure.PlaceMenuRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlaceMenuServiceTest {
    private final PlaceMenuRepository menuRepository = mock(PlaceMenuRepository.class);
    private final MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
    private final MerchantPlaceCapabilityPolicy capabilityPolicy = mock(MerchantPlaceCapabilityPolicy.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC);
    private PlaceMenuService service;

    @BeforeEach
    void setUp() {
        service = new PlaceMenuService(menuRepository, placeRepository, capabilityPolicy, clock);
        when(placeRepository.findById(10L)).thenReturn(Optional.of(MapPlace.builder().id(10L).build()));
        when(menuRepository.save(any(PlaceMenu.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsMenuWithExplicitCurrencyAndPrice() {
        PlaceMenuResponse response = service.create(7L, 10L,
                new PlaceMenuCreateRequest("짜장면", "대표 메뉴", 9000L, MenuCurrency.KRW,
                        "https://cdn.example/menu.jpg", 0));

        assertThat(response.name()).isEqualTo("짜장면");
        assertThat(response.priceAmount()).isEqualTo(9000L);
        assertThat(response.currency()).isEqualTo(MenuCurrency.KRW);
        assertThat(response.status()).isEqualTo(PlaceMenuStatus.AVAILABLE);
        verify(capabilityPolicy).require(7L, 10L, MerchantPlaceCapability.PRODUCT_MANAGE);
    }

    @Test
    void returnsOnlyPublicMenusInDisplayOrder() {
        PlaceMenu available = PlaceMenu.create(10L, 7L, "A", null, 1000L, MenuCurrency.KRW, null, 1,
                LocalDateTime.now(clock));
        PlaceMenu hidden = PlaceMenu.create(10L, 7L, "B", null, 1000L, MenuCurrency.KRW, null, 0,
                LocalDateTime.now(clock));
        hidden.changeStatus(PlaceMenuStatus.HIDDEN, LocalDateTime.now(clock));
        when(menuRepository.findAllByPlaceIdAndStatusInOrderByDisplayOrderAscIdAsc(eq(10L), anyCollection()))
                .thenReturn(List.of(available));

        assertThat(service.listPublic(10L)).extracting(PlaceMenuResponse::name).containsExactly("A");
    }

    @Test
    void rejectsMenuAccessWhenActorLacksPlaceCapability() {
        doThrow(new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_PERMISSION_REQUIRED)).when(capabilityPolicy)
                .require(99L, 10L, MerchantPlaceCapability.PRODUCT_MANAGE);

        assertThatThrownBy(() -> service.listOwned(99L, 10L))
                .isInstanceOfSatisfying(PlaceMenuException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(PlaceMenuErrorCode.MENU_FORBIDDEN));
    }
}
