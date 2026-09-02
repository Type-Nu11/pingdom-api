package com.typenull.pingdom.menu.application;

import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceCapability;
import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceCapabilityPolicy;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.menu.api.dto.*;
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

@Service
@RequiredArgsConstructor
public class PlaceMenuService {
    private static final List<PlaceMenuStatus> PUBLIC_STATUSES = List.of(PlaceMenuStatus.AVAILABLE,
            PlaceMenuStatus.SOLD_OUT);
    private final PlaceMenuRepository menuRepository;
    private final MapPlaceRepository placeRepository;
    private final MerchantPlaceCapabilityPolicy capabilityPolicy;
    private final Clock clock;

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

    @Transactional
    public PlaceMenuResponse reorder(Long userId, Long placeId, Long menuId, PlaceMenuOrderRequest request) {
        requirePlace(placeId);
        requireManage(userId, placeId);
        PlaceMenu target = findMenu(menuId, placeId);
        List<PlaceMenu> menus = menuRepository.findAllByPlaceIdForUpdateOrderByDisplayOrderAscIdAsc(placeId);
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
    public List<PlaceMenuResponse> listPublic(Long placeId) {
        MapPlace place = requirePlace(placeId);
        if (place.getDiscoveryStatus() != PlaceDiscoveryStatus.VISIBLE
                || place.getOperatingStatus() != PlaceOperatingStatus.OPERATING) {
            throw new PlaceMenuException(PlaceMenuErrorCode.MENU_PLACE_NOT_FOUND);
        }
        return menuRepository.findAllByPlaceIdAndStatusInOrderByDisplayOrderAscIdAsc(placeId, PUBLIC_STATUSES).stream()
                .map(PlaceMenuResponse::from).toList();
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
