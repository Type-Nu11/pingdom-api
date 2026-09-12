package com.typenull.pingdom.menu.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceCapabilityPolicy;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.menu.api.dto.PlaceMenuPublicResponse;
import com.typenull.pingdom.menu.application.PlaceMenuService;
import com.typenull.pingdom.menu.application.currency.CurrencyExchangeRate;
import com.typenull.pingdom.menu.application.currency.CurrencyExchangeRateClient;
import com.typenull.pingdom.menu.application.currency.MenuDisplayCurrencyResolver;
import com.typenull.pingdom.menu.application.currency.MenuPriceConversionService;
import com.typenull.pingdom.menu.domain.MenuCurrency;
import com.typenull.pingdom.menu.domain.PlaceMenu;
import com.typenull.pingdom.menu.infrastructure.PlaceMenuRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MenuPriceConversionIntegrationTest {

    private final PlaceMenuRepository menuRepository = mock(PlaceMenuRepository.class);
    private final MapPlaceRepository placeRepository = mock(MapPlaceRepository.class);
    private final MerchantPlaceCapabilityPolicy capabilityPolicy = mock(MerchantPlaceCapabilityPolicy.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-12T00:00:00Z"), ZoneOffset.UTC);
    private final PlaceMenu menu = PlaceMenu.create(10L, 7L, "짜장면", null, 9000L, MenuCurrency.KRW,
            null, 0, LocalDateTime.now(clock));

    @BeforeEach
    void setUp() {
        when(placeRepository.findById(10L)).thenReturn(Optional.of(MapPlace.builder().id(10L).build()));
        when(menuRepository.findAllByPlaceIdAndStatusInOrderByDisplayOrderAscIdAsc(eq(10L), anyCollection()))
                .thenReturn(List.of(menu));
    }

    @Test
    void convertsMenuPriceUsingEachAuthenticatedUsersCountryCurrency() {
        User usUser = mock(User.class);
        User jpUser = mock(User.class);
        when(userRepository.findById(99L)).thenReturn(Optional.of(usUser));
        when(userRepository.findById(100L)).thenReturn(Optional.of(jpUser));
        when(usUser.getCountry()).thenReturn("US");
        when(jpUser.getCountry()).thenReturn("JP");

        PlaceMenuService service = serviceWith((sourceCurrency, targetCurrency) -> switch (targetCurrency) {
            case USD -> Optional.of(rate(sourceCurrency, targetCurrency, "0.000714"));
            case JPY -> Optional.of(rate(sourceCurrency, targetCurrency, "0.113888"));
            default -> Optional.empty();
        });

        PlaceMenuPublicResponse usResponse = service.listPublic(10L, 99L).getFirst();
        PlaceMenuPublicResponse jpResponse = service.listPublic(10L, 100L).getFirst();

        assertThat(usResponse.priceAmount()).isEqualTo(9000L);
        assertThat(usResponse.currency()).isEqualTo(MenuCurrency.KRW);
        assertThat(usResponse.convertedPrice().amount()).isEqualByComparingTo("6.43");
        assertThat(usResponse.convertedPrice().currency()).isEqualTo(MenuCurrency.USD);
        assertThat(jpResponse.priceAmount()).isEqualTo(9000L);
        assertThat(jpResponse.currency()).isEqualTo(MenuCurrency.KRW);
        assertThat(jpResponse.convertedPrice().amount()).isEqualByComparingTo("1025");
        assertThat(jpResponse.convertedPrice().currency()).isEqualTo(MenuCurrency.JPY);
    }

    @Test
    void keepsOriginalPriceWithoutRateLookupForUnauthenticatedOrDefaultCurrency() {
        CurrencyExchangeRateClient exchangeRateClient = mock(CurrencyExchangeRateClient.class);
        User unknownCountryUser = mock(User.class);
        when(userRepository.findById(99L)).thenReturn(Optional.of(unknownCountryUser));
        when(unknownCountryUser.getCountry()).thenReturn("UNKNOWN");
        PlaceMenuService service = serviceWith(exchangeRateClient);

        PlaceMenuPublicResponse unauthenticatedResponse = service.listPublic(10L).getFirst();
        PlaceMenuPublicResponse defaultCurrencyResponse = service.listPublic(10L, 99L).getFirst();

        assertThat(unauthenticatedResponse.priceAmount()).isEqualTo(9000L);
        assertThat(unauthenticatedResponse.currency()).isEqualTo(MenuCurrency.KRW);
        assertThat(unauthenticatedResponse.convertedPrice()).isNull();
        assertThat(defaultCurrencyResponse.priceAmount()).isEqualTo(9000L);
        assertThat(defaultCurrencyResponse.currency()).isEqualTo(MenuCurrency.KRW);
        assertThat(defaultCurrencyResponse.convertedPrice()).isNull();
        verifyNoInteractions(exchangeRateClient);
    }

    private PlaceMenuService serviceWith(CurrencyExchangeRateClient exchangeRateClient) {
        return new PlaceMenuService(menuRepository, placeRepository, capabilityPolicy, userRepository,
                new MenuDisplayCurrencyResolver(), new MenuPriceConversionService(exchangeRateClient), clock);
    }

    private CurrencyExchangeRate rate(MenuCurrency sourceCurrency, MenuCurrency targetCurrency, String value) {
        return new CurrencyExchangeRate(sourceCurrency, targetCurrency, new BigDecimal(value), LocalDate.of(2026, 9, 10));
    }
}
