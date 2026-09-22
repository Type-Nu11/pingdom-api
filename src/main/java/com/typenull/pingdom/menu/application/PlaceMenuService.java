package com.typenull.pingdom.menu.application;

import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceCapability;
import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceCapabilityPolicy;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.menu.api.dto.*;
import com.typenull.pingdom.menu.application.currency.MenuDisplayCurrencyResolver;
import com.typenull.pingdom.menu.application.currency.MenuPriceConversionService;
import com.typenull.pingdom.menu.domain.*;
import com.typenull.pingdom.menu.domain.exception.*;
import com.typenull.pingdom.menu.infrastructure.PlaceMenuRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PRODUCT_MANAGE 권한으로 장소 메뉴를 관리하고 운영·노출 가능한 장소의 공개 메뉴를 조회합니다.
 * 공개 목록에는 AVAILABLE과 SOLD_OUT을 포함하며, 국가별 환산 가격은 원본 가격과 별도로 추가합니다.
 */
@Service
@RequiredArgsConstructor
public class PlaceMenuService {
    private static final List<PlaceMenuStatus> PUBLIC_STATUSES = List.of(PlaceMenuStatus.AVAILABLE,
            PlaceMenuStatus.SOLD_OUT);
    private final PlaceMenuRepository menuRepository;
    private final MapPlaceRepository placeRepository;
    private final MerchantPlaceCapabilityPolicy capabilityPolicy;
    private final UserRepository userRepository;
    private final MenuDisplayCurrencyResolver displayCurrencyResolver;
    private final MenuPriceConversionService priceConversionService;
    private final Clock clock;

    /**
     * 장소 존재와 상품 관리 권한을 확인해 메뉴를 저장하고 생성 응답을 반환합니다.
     * 도메인의 이름·가격·통화 등 입력 조건 위반은 INVALID_MENU_INPUT으로 변환합니다.
     */
    @Transactional
    public PlaceMenuResponse create(Long userId, Long placeId, PlaceMenuCreateRequest request) {
        requirePlace(placeId);
        requireManage(userId, placeId);
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            return PlaceMenuResponse.from(menuRepository.save(PlaceMenu.create(placeId, userId, request.name(),
                    request.description(), request.priceAmount(), request.currency(), request.imageUrl(),
                    request.displayOrder(), now)));
        } catch (IllegalArgumentException exception) {
            throw new PlaceMenuException(PlaceMenuErrorCode.INVALID_MENU_INPUT);
        }
    }

    @Transactional(readOnly = true)
    public List<PlaceMenuResponse> listOwned(Long userId, Long placeId) {
        requirePlace(placeId);
        requireManage(userId, placeId);
        return menuRepository.findAllByPlaceIdOrderByDisplayOrderAscIdAsc(placeId).stream()
                .map(PlaceMenuResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PlaceMenuResponse getOwned(Long userId, Long placeId, Long menuId) {
        requirePlace(placeId);
        requireManage(userId, placeId);
        return PlaceMenuResponse.from(findMenu(menuId, placeId));
    }

    /**
     * 장소 존재·관리 권한·메뉴 소속을 확인하고 메뉴 행을 잠가 이름·설명·가격·통화·이미지를 갱신합니다.
     * 비활성 메뉴는 상태 충돌로 거절하고 입력 오류는 메뉴 입력 오류로 변환해 변경 응답을 반환합니다.
     */
    @Transactional
    public PlaceMenuResponse update(Long userId, Long placeId, Long menuId, PlaceMenuUpdateRequest request) {
        requirePlace(placeId);
        requireManage(userId, placeId);
        PlaceMenu menu = findMenu(menuId, placeId);
        if (menu.getStatus() == PlaceMenuStatus.INACTIVE) {
            throw new PlaceMenuException(PlaceMenuErrorCode.MENU_STATE_CONFLICT);
        }
        try {
            menu.update(request.name(), request.description(), request.priceAmount(), request.currency(),
                    request.imageUrl(), LocalDateTime.now(clock));
            return PlaceMenuResponse.from(menu);
        } catch (IllegalArgumentException exception) {
            throw new PlaceMenuException(PlaceMenuErrorCode.INVALID_MENU_INPUT);
        }
    }

    @Transactional
    public PlaceMenuResponse changeStatus(Long userId, Long placeId, Long menuId, PlaceMenuStatus status) {
        requirePlace(placeId);
        requireManage(userId, placeId);
        PlaceMenu menu = findMenu(menuId, placeId);
        menu.changeStatus(status, LocalDateTime.now(clock));
        return PlaceMenuResponse.from(menu);
    }

    /**
     * 메뉴를 목적 인덱스로 이동한 뒤 장소의 전체 메뉴를 0부터 연속 순서로 다시 기록합니다.
     * 범위를 넘는 인덱스는 마지막 위치로 보정하며 INACTIVE 메뉴도 재정렬 대상에 포함합니다.
     */
    @Transactional
    public PlaceMenuResponse reorder(Long userId, Long placeId, Long menuId, PlaceMenuOrderRequest request) {
        requirePlace(placeId);
        requireManage(userId, placeId);
        PlaceMenu target = findMenu(menuId, placeId);
        List<PlaceMenu> menus = menuRepository.findAllByPlaceIdOrderByDisplayOrderAscIdAscForUpdate(placeId);
        int destination = Math.min(request.displayOrder(), menus.size() - 1);
        menus.remove(target);
        menus.add(destination, target);
        LocalDateTime now = LocalDateTime.now(clock);
        for (int index = 0; index < menus.size(); index++) menus.get(index).changeDisplayOrder(index, now);
        return PlaceMenuResponse.from(target);
    }

    @Transactional
    public void deactivate(Long userId, Long placeId, Long menuId) {
        changeStatus(userId, placeId, menuId, PlaceMenuStatus.INACTIVE);
    }

    @Transactional(readOnly = true)
    public List<PlaceMenuPublicResponse> listPublic(Long placeId) {
        return listPublic(placeId, null);
    }

    /**
     * 노출 가능하고 영업 중인 장소에서 AVAILABLE·SOLD_OUT 메뉴를 표시 순서·ID 순으로 반환합니다.
     * 숨김·비영업 장소는 없는 대상으로 처리하며 요청 회원의 국가로 표시 통화를 정해 환산 가격을 함께 제공합니다.
     */
    @Transactional(readOnly = true)
    public List<PlaceMenuPublicResponse> listPublic(Long placeId, Long userId) {
        MapPlace place = requirePlace(placeId);
        if (place.getDiscoveryStatus() != PlaceDiscoveryStatus.VISIBLE
                || place.getOperatingStatus() != PlaceOperatingStatus.OPERATING) {
            throw new PlaceMenuException(PlaceMenuErrorCode.MENU_PLACE_NOT_FOUND);
        }
        String country = userId == null ? null : userRepository.findById(userId)
                .map(user -> user.getCountry())
                .orElse(null);
        MenuCurrency displayCurrency = displayCurrencyResolver.resolve(country);
        return menuRepository.findAllByPlaceIdAndStatusInOrderByDisplayOrderAscIdAsc(placeId, PUBLIC_STATUSES).stream()
                .map(menu -> PlaceMenuPublicResponse.from(menu, priceConversionService.convert(menu, displayCurrency))).toList();
    }

    private MapPlace requirePlace(Long placeId) {
        return placeRepository.findById(placeId)
                .orElseThrow(() -> new PlaceMenuException(PlaceMenuErrorCode.MENU_PLACE_NOT_FOUND));
    }

    private void requireManage(Long userId, Long placeId) {
        try {
            capabilityPolicy.require(userId, placeId, MerchantPlaceCapability.PRODUCT_MANAGE);
        } catch (MerchantOwnerException exception) {
            throw new PlaceMenuException(PlaceMenuErrorCode.MENU_FORBIDDEN);
        }
    }

    private PlaceMenu findMenu(Long menuId, Long placeId) {
        PlaceMenu menu = menuRepository.findByIdForUpdate(menuId)
                .orElseThrow(() -> new PlaceMenuException(PlaceMenuErrorCode.MENU_NOT_FOUND));
        if (!menu.getPlaceId().equals(placeId)) throw new PlaceMenuException(PlaceMenuErrorCode.MENU_NOT_FOUND);
        return menu;
    }
}
