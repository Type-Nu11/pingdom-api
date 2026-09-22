package com.typenull.pingdom.place.application.service.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.place.api.dto.registration.NaverAddressSearchResponse;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.List;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Service;

/** Naver Geocoding 응답을 신규 장소 신청 API가 사용하는 주소·좌표 DTO로 변환합니다. */
@Service
public class NaverAddressSearchService {
    private static final int MAX_RESULTS = 10;

    private final NaverAddressSearchClient client;

    /** 외부 Geocoding 호출 책임은 client에 위임하고 응답 정규화만 수행합니다. */
    public NaverAddressSearchService(NaverAddressSearchClient client) {
        this.client = client;
    }

    /**
     * 공백 검색어는 외부 호출 전에 거부하고, 정상 응답은 최대 10건의 WGS84 주소 후보로 반환합니다.
     * 조회 과정은 장소 신청·Merchant 상태를 변경하지 않습니다.
     */
    public NaverAddressSearchResponse search(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
        }
        JsonNode addresses = client.search(normalized);
        List<NaverAddressSearchResponse.Item> items = addresses.isArray()
                ? StreamSupport.stream(addresses.spliterator(), false).limit(MAX_RESULTS).map(this::item).toList()
                : List.of();
        return new NaverAddressSearchResponse(items);
    }

    /** Naver의 x 경도·y 위도를 API 응답의 longitude·latitude로 순서를 맞춰 변환합니다. */
    private NaverAddressSearchResponse.Item item(JsonNode address) {
        try {
            return new NaverAddressSearchResponse.Item(
                    address.path("roadAddress").asText(),
                    address.path("jibunAddress").asText(),
                    postalCode(address.path("addressElements")),
                    Double.parseDouble(address.path("y").asText()),
                    Double.parseDouble(address.path("x").asText())
            );
        } catch (NumberFormatException exception) {
            throw new MapException(MapErrorCode.NAVER_ADDRESS_SEARCH_FAILED);
        }
    }

    /** addressElements 중 POSTAL_CODE 유형이 있는 경우에만 우편번호를 추출합니다. */
    private String postalCode(JsonNode addressElements) {
        if (!addressElements.isArray()) {
            return null;
        }
        for (JsonNode element : addressElements) {
            if (hasPostalCodeType(element.path("types"))) {
                String postalCode = element.path("longName").asText();
                return postalCode.isBlank() ? null : postalCode;
            }
        }
        return null;
    }

    /** 주소 구성 요소의 유형 목록에 POSTAL_CODE가 포함되는지 확인합니다. */
    private boolean hasPostalCodeType(JsonNode types) {
        if (!types.isArray()) {
            return false;
        }
        for (JsonNode type : types) {
            if ("POSTAL_CODE".equals(type.asText())) {
                return true;
            }
        }
        return false;
    }
}
