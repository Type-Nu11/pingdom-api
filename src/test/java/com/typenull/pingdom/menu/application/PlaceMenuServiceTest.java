package com.typenull.pingdom.menu.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceCapability;
import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceCapabilityPolicy;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.menu.api.dto.*;
import com.typenull.pingdom.menu.application.currency.MenuDisplayCurrencyResolver;
import com.typenull.pingdom.menu.application.currency.MenuPriceConversionService;
import com.typenull.pingdom.menu.domain.*;
import com.typenull.pingdom.menu.domain.exception.*;
import com.typenull.pingdom.menu.infrastructure.PlaceMenuRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlaceMenuServiceTest {
    private final PlaceMenuRepository menuRepository = mock(PlaceMenuRepository.class);
    private final MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
    private final MerchantPlaceCapabilityPolicy capabilityPolicy = mock(MerchantPlaceCapabilityPolicy.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MenuDisplayCurrencyResolver displayCurrencyResolver = mock(MenuDisplayCurrencyResolver.class);
    private final MenuPriceConversionService priceConversionService = mock(MenuPriceConversionService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC);
    private PlaceMenuService service;

    @BeforeEach
    void setUp() {
        service = new PlaceMenuService(menuRepository, placeRepository, capabilityPolicy, userRepository,
                displayCurrencyResolver, priceConversionService, clock);
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
        when(displayCurrencyResolver.resolve(null)).thenReturn(MenuCurrency.KRW);

        assertThat(service.listPublic(10L)).extracting(PlaceMenuPublicResponse::name).containsExactly("A");
    }

    @Test
    void usesRequestingUsersCountryToSelectConvertedMenuPrice() {
        PlaceMenu menu = PlaceMenu.create(10L, 7L, "짜장면", null, 9000L, MenuCurrency.KRW, null, 0,
                LocalDateTime.now(clock));
        User user = mock(User.class);
        when(menuRepository.findAllByPlaceIdAndStatusInOrderByDisplayOrderAscIdAsc(eq(10L), anyCollection()))
                .thenReturn(List.of(menu));
        when(userRepository.findById(99L)).thenReturn(Optional.of(user));
        when(user.getCountry()).thenReturn("US");
        when(displayCurrencyResolver.resolve("US")).thenReturn(MenuCurrency.USD);
        when(priceConversionService.convert(menu, MenuCurrency.USD)).thenReturn(
                new MenuConvertedPriceResponse(new BigDecimal("6.43"), MenuCurrency.USD, LocalDate.of(2026, 9, 10)));

        PlaceMenuPublicResponse response = service.listPublic(10L, 99L).getFirst();

        assertThat(response.convertedPrice().currency()).isEqualTo(MenuCurrency.USD);
        assertThat(response.convertedPrice().amount()).isEqualByComparingTo("6.43");
    }

    @Test
    void isolatesConvertedMenuPriceByRequestingUsersCountry() {
        PlaceMenu menu = PlaceMenu.create(10L, 7L, "짜장면", null, 9000L, MenuCurrency.KRW, null, 0,
                LocalDateTime.now(clock));
        User usUser = mock(User.class);
        User jpUser = mock(User.class);
        when(menuRepository.findAllByPlaceIdAndStatusInOrderByDisplayOrderAscIdAsc(eq(10L), anyCollection()))
                .thenReturn(List.of(menu));
        when(userRepository.findById(99L)).thenReturn(Optional.of(usUser));
        when(userRepository.findById(100L)).thenReturn(Optional.of(jpUser));
        when(usUser.getCountry()).thenReturn("US");
        when(jpUser.getCountry()).thenReturn("JP");
        when(displayCurrencyResolver.resolve("US")).thenReturn(MenuCurrency.USD);
        when(displayCurrencyResolver.resolve("JP")).thenReturn(MenuCurrency.JPY);
        when(priceConversionService.convert(menu, MenuCurrency.USD)).thenReturn(
                new MenuConvertedPriceResponse(new BigDecimal("6.43"), MenuCurrency.USD, LocalDate.of(2026, 9, 10)));
        when(priceConversionService.convert(menu, MenuCurrency.JPY)).thenReturn(
                new MenuConvertedPriceResponse(new BigDecimal("1025"), MenuCurrency.JPY, LocalDate.of(2026, 9, 10)));

        PlaceMenuPublicResponse usResponse = service.listPublic(10L, 99L).getFirst();
        PlaceMenuPublicResponse jpResponse = service.listPublic(10L, 100L).getFirst();

        assertThat(usResponse.convertedPrice().currency()).isEqualTo(MenuCurrency.USD);
        assertThat(jpResponse.convertedPrice().currency()).isEqualTo(MenuCurrency.JPY);
        verify(priceConversionService).convert(menu, MenuCurrency.USD);
        verify(priceConversionService).convert(menu, MenuCurrency.JPY);
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
