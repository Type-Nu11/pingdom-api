package com.typenull.pingdom.menu.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceCapabilityPolicy;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.menu.api.dto.PlaceMenuPublicResponse;
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

    /**
     * 장소 10과 메뉴 조회 결과를 고정해 실제 통화 선택·환산 서비스 조합을 검증할 공통 입력을 구성.
     */
    @BeforeEach
    void setUp() {
        when(placeRepository.findById(10L)).thenReturn(Optional.of(MapPlace.builder().id(10L).build()));
        when(menuRepository.findAllByPlaceIdAndStatusInOrderByDisplayOrderAscIdAsc(eq(10L), anyCollection()))
                .thenReturn(List.of(menu));
    }

    /**
     * 미국·일본 사용자가 같은 메뉴를 조회하면 원가격을 유지하며 각각 USD 6.43·JPY 1,025로 환산되는지 검증.
     * 저장소·환율 입력에 대역을 사용하며 외부 API 통합 실행은 제외.
     */
    @Test
    void convertsPricePerUserCountry() {
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

    /**
     * 비로그인 또는 알 수 없는 국가 사용자는 원가격 9,000 KRW와 null 환산 값을 받고 환율 조회가 호출되지 않는지 검증.
     */
    @Test
    void skipsDefaultCurrencyRateLookup() {
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

    /**
     * 같은 통화 쌍의 환율 공급자가 장애여도 하나의 메뉴 목록에서는 첫 실패 결과를 재사용하고,
     * 다음 목록 요청에서는 복구 여부를 확인하도록 다시 조회하는지 검증.
     */
    @Test
    void reusesFailedRateOnlyWithinOneMenuListRequest() {
        PlaceMenu secondKrwMenu = PlaceMenu.create(10L, 7L, "짬뽕", null, 10000L, MenuCurrency.KRW,
                null, 1, LocalDateTime.now(clock));
        when(menuRepository.findAllByPlaceIdAndStatusInOrderByDisplayOrderAscIdAsc(eq(10L), anyCollection()))
                .thenReturn(List.of(menu, secondKrwMenu));
        User usUser = mock(User.class);
        when(userRepository.findById(99L)).thenReturn(Optional.of(usUser));
        when(usUser.getCountry()).thenReturn("US");
        CurrencyExchangeRateClient exchangeRateClient = mock(CurrencyExchangeRateClient.class);
        when(exchangeRateClient.findRate(MenuCurrency.KRW, MenuCurrency.USD)).thenReturn(Optional.empty());
        PlaceMenuService service = serviceWith(exchangeRateClient);

        List<PlaceMenuPublicResponse> first = service.listPublic(10L, 99L);
        List<PlaceMenuPublicResponse> second = service.listPublic(10L, 99L);

        assertThat(first).hasSize(2).allSatisfy(response -> assertThat(response.convertedPrice()).isNull());
        assertThat(second).hasSize(2).allSatisfy(response -> assertThat(response.convertedPrice()).isNull());
        verify(exchangeRateClient, times(2)).findRate(MenuCurrency.KRW, MenuCurrency.USD);
    }

    /**
     * 지정된 환율 클라이언트를 실제 국가 통화 해석기·환산 서비스와 조합해 메뉴 서비스를 생성.
     */
    private PlaceMenuService serviceWith(CurrencyExchangeRateClient exchangeRateClient) {
        return new PlaceMenuService(menuRepository, placeRepository, capabilityPolicy, userRepository,
                new MenuDisplayCurrencyResolver(), new MenuPriceConversionService(exchangeRateClient), clock);
    }

    /**
     * 2026-09-10 기준일의 통화 쌍 환율을 문자열 기반 BigDecimal로 만들어 반올림 테스트 입력을 제공.
     */
    private CurrencyExchangeRate rate(MenuCurrency sourceCurrency, MenuCurrency targetCurrency, String value) {
        return new CurrencyExchangeRate(sourceCurrency, targetCurrency, new BigDecimal(value), LocalDate.of(2026, 9, 10));
    }
}
