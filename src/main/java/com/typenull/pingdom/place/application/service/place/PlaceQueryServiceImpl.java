package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.availability.application.PlaceAvailabilityService;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerPublicResponse;
import com.typenull.pingdom.identity.application.service.merchant.MerchantOwnerPublicQueryService;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceInformationRepository;
import com.typenull.pingdom.offer.application.TouristOfferService;
import com.typenull.pingdom.place.api.dto.place.autocomplete.PlaceAutocompleteItem;
import com.typenull.pingdom.place.api.dto.place.autocomplete.PlaceAutocompleteResponse;
import com.typenull.pingdom.place.api.dto.place.detail.PlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.place.detail.PlaceVisitDecisionEventResponse;
import com.typenull.pingdom.place.api.dto.place.detail.PlaceVisitDecisionMerchantInformationResponse;
import com.typenull.pingdom.place.api.dto.place.detail.PlaceVisitDecisionResponse;
import com.typenull.pingdom.place.api.dto.place.card.TouristPlaceCardResponse;
import com.typenull.pingdom.place.api.dto.place.list.PlaceListItem;
import com.typenull.pingdom.place.api.dto.place.list.PlaceListResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingExceptionResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingTimeRangeResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceRegularOperatingHourResponse;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeResponse;
import com.typenull.pingdom.place.application.service.place.operating.PlaceCurrentOperatingState;
import com.typenull.pingdom.place.application.service.place.operating.PlaceOperatingHoursEvaluator;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.event.PlaceEventPublicationStatus;
import com.typenull.pingdom.place.domain.place.category.PlaceCategoryPolicy;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingException;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingTimeRange;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingHour;
import com.typenull.pingdom.place.domain.place.operating.notice.PlaceOperatingNoticeStatus;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationSummary;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.event.PlaceEventRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceOperatingNoticeRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceSearchQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceInformationVerificationSummaryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceSearchQueryRepository.PlaceTouristCategoryProjection;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceSearchQueryRepository.PlaceSearchProjection;
import com.typenull.pingdom.shared.observability.PlaceVisitDecisionMetrics;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.EnumSet;
import java.util.Comparator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
/** 장소 검색과 상세 조회에 필요한 영속성 조회 결과를 공개 응답으로 조합합니다. */
public class PlaceQueryServiceImpl implements PlaceQueryService {

    private static final int MAX_SEARCH_LIMIT = 100;
    private static final double KM_PER_LATITUDE_DEGREE = 111.32d;
    private static final double MIN_COSINE_FOR_LONGITUDE_DELTA = 0.000001d;
    private static final int AUTOCOMPLETE_MIN_LENGTH = 2;
    private static final int AUTOCOMPLETE_MAX_LIMIT = 10;
    private static final int AUTOCOMPLETE_CANDIDATE_FETCH_SIZE = 100;
    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceSearchQueryRepository placeSearchQueryRepository;
    private final MerchantOwnerPublicQueryService merchantOwnerPublicQueryService;
    private final PlaceOperatingNoticeRepository placeOperatingNoticeRepository;
    private final PlaceInformationVerificationSummaryRepository placeInformationVerificationSummaryRepository;
    private final PlaceOperatingHoursEvaluator operatingHoursEvaluator;
    private final MerchantPlaceInformationRepository merchantPlaceInformationRepository;
    private final PlaceEventRepository placeEventRepository;
    private final PlaceAvailabilityService placeAvailabilityService;
    private final TouristOfferService touristOfferService;
    private final PlaceVisitDecisionMetrics placeVisitDecisionMetrics;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    /** 검색 조건을 조회 쿼리에 전달하고 장소별 부가 데이터를 일괄 조합합니다. */
    public PlaceListResponse listPlaces(PlaceSearchCondition condition) {
        if (condition == null) {
            throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
        }

        int safePage = Math.max(condition.page(), 1);
        int safeLimit = Math.max(1, Math.min(condition.limit(), MAX_SEARCH_LIMIT));
        Pageable pageable = PageRequest.of(safePage - 1, safeLimit);
        PlaceSearchSort searchSort = PlaceSearchSort.from(condition.sort());
        LocationSearch locationSearch = LocationSearch.from(
                condition.latitude(),
                condition.longitude(),
                condition.radiusKm()
        );
        if (searchSort == PlaceSearchSort.NEAREST && !locationSearch.enabled()) {
            throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
        }

        String keywordPattern = toLikePattern(condition.keyword());
        String category = normalizeCategory(condition.category());
        TouristCategory touristCategory = normalizeTouristCategory(condition.touristCategory());

        Page<PlaceSearchProjection> placePage = placeSearchQueryRepository.searchPlaces(
                keywordPattern,
                category,
                touristCategory == null ? null : touristCategory.name(),
                PlaceOperatingStatus.OPERATING.name(),
                PlaceDiscoveryStatus.VISIBLE.name(),
                locationSearch.enabled(),
                locationSearch.latitude(),
                locationSearch.longitude(),
                locationSearch.radiusMeters(),
                searchSort.name(),
                pageable
        );

        Map<Long, Set<TouristCategory>> touristCategoriesByPlaceId = loadTouristCategories(
                placePage.getContent().stream().map(PlaceSearchProjection::getId).toList()
        );
        Map<Long, PlaceInformationVerificationSummary> verificationSummariesByPlaceId =
                loadVerificationSummaries(placePage.getContent().stream().map(PlaceSearchProjection::getId).toList());
        List<PlaceListItem> places = placePage.getContent()
                .stream()
                .map(place -> toListItem(
                        place,
                        touristCategoriesByPlaceId.getOrDefault(place.getId(), Set.of()),
                        verificationSummariesByPlaceId.get(place.getId())
                ))
                .toList();

        return PlaceListResponse.of(
                places,
                safePage,
                safeLimit,
                placePage.getTotalElements(),
                placePage.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    /** 후보를 제한한 뒤 이름·주소·거리 우선순위로 자동완성 결과를 정렬합니다. */
    public PlaceAutocompleteResponse autocompletePlaces(String keyword, int limit, Double latitude, Double longitude) {
        String normalizedKeyword = normalizeAutocompleteKeyword(keyword);
        int safeLimit = Math.max(1, Math.min(limit, AUTOCOMPLETE_MAX_LIMIT));

        if (normalizedKeyword.length() < AUTOCOMPLETE_MIN_LENGTH) {
            return new PlaceAutocompleteResponse(normalizedKeyword, safeLimit, 0, List.of());
        }
        if ((latitude == null) != (longitude == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude와 longitude는 함께 전달해야 합니다.");
        }

        Pageable pageable = PageRequest.of(
                0,
                AUTOCOMPLETE_CANDIDATE_FETCH_SIZE,
                Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"))
        );

        List<PlaceAutocompleteItem> places = placeSearchQueryRepository.findAutocompleteCandidates(
                normalizedKeyword,
                PlaceOperatingStatus.OPERATING,
                PlaceDiscoveryStatus.VISIBLE,
                pageable
        )
                .stream()
                .sorted((first, second) -> compareAutocompletePlaces(
                        first,
                        second,
                        normalizedKeyword,
                        latitude,
                        longitude
                ))
                .limit(safeLimit)
                .map(place -> toAutocompleteItem(place, latitude, longitude))
                .toList();

        return new PlaceAutocompleteResponse(normalizedKeyword, safeLimit, places.size(), places);
    }

    @Override
    @Transactional(readOnly = true)
    /** 장소 본문과 운영·검증·상업 정보를 하나의 상세 DTO로 변환합니다. */
    public PlaceDetailResponse getPlace(Long placeId) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
        if (!mapPlace.isOperating() || !mapPlace.isVisibleInDiscovery()) {
            throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
        }

        return toPlaceDetailResponse(
                mapPlace,
                merchantOwnerPublicQueryService.findByPlaceId(mapPlace.getId())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PlaceVisitDecisionResponse getPlaceVisitDecision(Long placeId) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
        if (!mapPlace.isVisibleInDiscovery()
                || mapPlace.getOperatingStatus() == PlaceOperatingStatus.PERMANENTLY_CLOSED) {
            throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
        }

        LocalDateTime checkedAt = LocalDateTime.now(clock);
        MerchantOwnerPublicResponse merchantOwner = merchantOwnerPublicQueryService.findByPlaceId(mapPlace.getId());
        // 임시 휴업은 방문 결정을 위해 상태와 공지를 노출하고, 영구 폐업만 공개 대상에서 제외한다.
        PlaceVisitDecisionResponse response = new PlaceVisitDecisionResponse(
                toPlaceDetailResponse(mapPlace, merchantOwner),
                publicMerchantInformation(mapPlace.getId(), merchantOwner),
                placeEventRepository.findOngoingPublishedByPlaceId(
                                mapPlace.getId(),
                                PlaceEventPublicationStatus.PUBLISHED,
                                checkedAt
                        )
                        .stream()
                        .map(event -> PlaceVisitDecisionEventResponse.from(event, checkedAt))
                        .toList(),
                placeAvailabilityService.listPublic(mapPlace.getId()),
                touristOfferService.list(mapPlace.getId(), 1, 20),
                checkedAt
        );
        placeVisitDecisionMetrics.recordViewed(mapPlace.getOperatingStatus());
        return response;
    }

    private PlaceDetailResponse toPlaceDetailResponse(
            MapPlace mapPlace,
            MerchantOwnerPublicResponse merchantOwner
    ) {

        PlaceCurrentOperatingState operatingState = operatingHoursEvaluator.evaluate(mapPlace);
        PlaceInformationVerificationSummary verificationSummary = loadVerificationSummary(mapPlace.getId());

        return new PlaceDetailResponse(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getEnglishName(),
                mapPlace.getAddress(),
                mapPlace.getRoadAddress(),
                mapPlace.getJibunAddress(),
                mapPlace.getPostalCode(),
                mapPlace.getGeocodingSource(),
                mapPlace.getOperatingStatus(),
                mapPlace.getOperatingStatusCheckedAt(),
                operatingState.currentlyOperating(),
                operatingState.checkedAt(),
                regularHours(mapPlace),
                operatingExceptions(mapPlace),
                activeOperatingNotices(mapPlace, operatingState.checkedAt()),
                mapPlace.getDescription(),
                mapPlace.getTouristSummary(),
                mapPlace.currentTouristCategories(),
                mapPlace.getPrimaryInformationSource(),
                mapPlace.getInformationVerificationStatus(),
                mapPlace.getInformationVerifiedAt(),
                mapPlace.getInformationEvidenceUpdatedAt(),
                verifiedEvidenceCount(verificationSummary),
                lastVerifiedAt(verificationSummary),
                lastVerifiedSourceType(verificationSummary),
                mapPlace.getLatitude(),
                mapPlace.getLongitude(),
                mapPlace.getRegistrant(),
                merchantOwner
        );
    }

    private PlaceVisitDecisionMerchantInformationResponse publicMerchantInformation(
            Long placeId,
            MerchantOwnerPublicResponse merchantOwner
    ) {
        // 비활성 Merchant의 과거 연락처·예약 링크는 관광객 응답에서 노출하지 않는다.
        if (merchantOwner == null) {
            return null;
        }
        return merchantPlaceInformationRepository.findByPlaceId(placeId)
                .map(PlaceVisitDecisionMerchantInformationResponse::from)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public TouristPlaceCardResponse getTouristPlaceCard(Long placeId) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
        if (!mapPlace.isVisibleInDiscovery() || mapPlace.getOperatingStatus() == PlaceOperatingStatus.PERMANENTLY_CLOSED) {
            // 숨김·영구 폐업 장소는 탐색 대상이 아니지만, 임시 휴업은 방문 판단을 위해 카드에 표시한다.
            throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
        }

        PlaceCurrentOperatingState operatingState = operatingHoursEvaluator.evaluate(mapPlace);
        PlaceInformationVerificationSummary verificationSummary = loadVerificationSummary(mapPlace.getId());
        return new TouristPlaceCardResponse(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getEnglishName(),
                mapPlace.getImageUrl(),
                mapPlace.getAddress(),
                mapPlace.getRoadAddress(),
                mapPlace.getGeocodingSource(),
                mapPlace.getOperatingStatus(),
                operatingState.currentlyOperating(),
                operatingState.checkedAt(),
                mapPlace.getCategory(),
                mapPlace.getTouristSummary(),
                mapPlace.currentTouristCategories(),
                mapPlace.getPrimaryInformationSource(),
                mapPlace.getInformationVerificationStatus(),
                mapPlace.getInformationVerifiedAt(),
                mapPlace.getInformationEvidenceUpdatedAt(),
                verifiedEvidenceCount(verificationSummary),
                lastVerifiedAt(verificationSummary),
                lastVerifiedSourceType(verificationSummary),
                mapPlace.getLatitude(),
                mapPlace.getLongitude()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PlaceListResponse listBookmarkedPlaces(Long userId, int page, int limit) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }

        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        Pageable pageable = PageRequest.of(safePage - 1, safeLimit);

        Page<MapPlace> placePage = placeSearchQueryRepository.findBookmarkedPlacesByUserId(
                userId,
                PlaceOperatingStatus.OPERATING,
                PlaceDiscoveryStatus.VISIBLE,
                pageable
        );
        Map<Long, Set<TouristCategory>> touristCategoriesByPlaceId = loadTouristCategories(
                placePage.getContent().stream().map(MapPlace::getId).toList()
        );
        Map<Long, PlaceInformationVerificationSummary> verificationSummariesByPlaceId =
                loadVerificationSummaries(placePage.getContent().stream().map(MapPlace::getId).toList());
        List<PlaceListItem> places = placePage.getContent().stream()
                .map(place -> toListItem(
                        place,
                        touristCategoriesByPlaceId.getOrDefault(place.getId(), Set.of()),
                        verificationSummariesByPlaceId.get(place.getId())
                ))
                .toList();

        return PlaceListResponse.of(
                places,
                safePage,
                safeLimit,
                placePage.getTotalElements(),
                placePage.getTotalPages()
        );
    }

    private PlaceListItem toListItem(
            MapPlace mapPlace,
            Set<TouristCategory> touristCategories,
            PlaceInformationVerificationSummary verificationSummary
    ) {
        return new PlaceListItem(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getEnglishName(),
                mapPlace.getAddress(),
                mapPlace.getRoadAddress(),
                mapPlace.getJibunAddress(),
                mapPlace.getPostalCode(),
                mapPlace.getGeocodingSource(),
                mapPlace.getOperatingStatus(),
                mapPlace.getOperatingStatusCheckedAt(),
                mapPlace.getCategory(),
                mapPlace.getTouristSummary(),
                touristCategories,
                mapPlace.getPrimaryInformationSource(),
                mapPlace.getInformationVerificationStatus(),
                mapPlace.getInformationVerifiedAt(),
                mapPlace.getInformationEvidenceUpdatedAt(),
                verifiedEvidenceCount(verificationSummary),
                lastVerifiedAt(verificationSummary),
                lastVerifiedSourceType(verificationSummary),
                mapPlace.getLatitude(),
                mapPlace.getLongitude(),
                null
        );
    }

    private PlaceListItem toListItem(
            PlaceSearchProjection projection,
            Set<TouristCategory> touristCategories,
            PlaceInformationVerificationSummary verificationSummary
    ) {
        Double distanceMeters = projection.getDistanceMeters();
        return new PlaceListItem(
                projection.getId(),
                projection.getName(),
                projection.getEnglishName(),
                projection.getAddress(),
                projection.getRoadAddress(),
                projection.getJibunAddress(),
                projection.getPostalCode(),
                projection.getGeocodingSource(),
                projection.getOperatingStatus(),
                projection.getOperatingStatusCheckedAt(),
                projection.getCategory(),
                projection.getTouristSummary(),
                touristCategories,
                projection.getPrimaryInformationSource(),
                projection.getInformationVerificationStatus(),
                projection.getInformationVerifiedAt(),
                projection.getInformationEvidenceUpdatedAt(),
                verifiedEvidenceCount(verificationSummary),
                lastVerifiedAt(verificationSummary),
                lastVerifiedSourceType(verificationSummary),
                projection.getLatitude(),
                projection.getLongitude(),
                distanceMeters == null ? null : Math.round(distanceMeters)
        );
    }

    private PlaceInformationVerificationSummary loadVerificationSummary(Long placeId) {
        return placeInformationVerificationSummaryRepository.findById(placeId).orElse(null);
    }

    private Map<Long, PlaceInformationVerificationSummary> loadVerificationSummaries(List<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PlaceInformationVerificationSummary> summaries = new HashMap<>();
        for (PlaceInformationVerificationSummary summary :
                placeInformationVerificationSummaryRepository.findAllById(placeIds)) {
            summaries.put(summary.getPlaceId(), summary);
        }
        return summaries;
    }

    private int verifiedEvidenceCount(PlaceInformationVerificationSummary summary) {
        return summary == null ? 0 : summary.getVerifiedEvidenceCount();
    }

    private LocalDateTime lastVerifiedAt(PlaceInformationVerificationSummary summary) {
        return summary == null ? null : summary.getLastVerifiedAt();
    }

    private PlaceInformationSourceType lastVerifiedSourceType(
            PlaceInformationVerificationSummary summary
    ) {
        return summary == null ? null : summary.getLastVerifiedSourceType();
    }

    private List<PlaceRegularOperatingHourResponse> regularHours(MapPlace mapPlace) {
        return mapPlace.currentRegularOperatingHours().stream()
                .sorted(Comparator.comparing(PlaceRegularOperatingHour::getDayOfWeek)
                        .thenComparing(PlaceRegularOperatingHour::getOpensAt)
                        .thenComparing(PlaceRegularOperatingHour::getClosesAt))
                .map(hour -> new PlaceRegularOperatingHourResponse(
                        hour.getDayOfWeek(),
                        hour.getOpensAt(),
                        hour.getClosesAt()
                ))
                .toList();
    }

    private List<PlaceOperatingExceptionResponse> operatingExceptions(MapPlace mapPlace) {
        return mapPlace.currentOperatingExceptions().stream()
                .map(exception -> new PlaceOperatingExceptionResponse(
                        exception.getExceptionDate(),
                        exception.isClosed(),
                        exception.currentHours().stream()
                                .sorted(Comparator.comparing(PlaceOperatingTimeRange::getOpensAt)
                                        .thenComparing(PlaceOperatingTimeRange::getClosesAt))
                                .map(hour -> new PlaceOperatingTimeRangeResponse(
                                        hour.getOpensAt(),
                                        hour.getClosesAt()
                                ))
                                .toList()
                ))
                .toList();
    }

    private List<PlaceOperatingNoticeResponse> activeOperatingNotices(MapPlace mapPlace, LocalDateTime checkedAt) {
        return placeOperatingNoticeRepository.findAllByPlace_IdAndStatusInOrderByStartsAtAscIdAsc(
                        mapPlace.getId(),
                        Set.of(PlaceOperatingNoticeStatus.ACTIVE)
                )
                .stream()
                .filter(notice -> notice.isVisibleAt(checkedAt))
                .map(notice -> PlaceOperatingNoticeResponse.from(notice, checkedAt))
                .toList();
    }

    private String toLikePattern(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return "%" + escapeLike(normalized.toLowerCase(Locale.ROOT)) + "%";
    }

    private String normalizeCategory(String value) {
        String normalized = PlaceCategoryPolicy.normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private TouristCategory normalizeTouristCategory(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }

        try {
            return TouristCategory.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private PlaceAutocompleteItem toAutocompleteItem(MapPlace mapPlace, Double latitude, Double longitude) {
        return new PlaceAutocompleteItem(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getEnglishName(),
                mapPlace.getAddress(),
                mapPlace.getRoadAddress(),
                mapPlace.getJibunAddress(),
                mapPlace.getPostalCode(),
                mapPlace.getGeocodingSource(),
                mapPlace.getOperatingStatus(),
                mapPlace.getOperatingStatusCheckedAt(),
                mapPlace.getCategory(),
                mapPlace.getLatitude(),
                mapPlace.getLongitude(),
                calculateDistanceMeters(latitude, longitude, mapPlace)
        );
    }

    private String normalizeAutocompleteKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        String normalizedKeyword = keyword.trim().replaceAll("\\s+", " ");
        if (normalizedKeyword.isEmpty()) {
            return "";
        }
        if (!normalizedKeyword.matches(".*[0-9A-Za-z가-힣].*")) {
            return "";
        }
        return normalizedKeyword;
    }

    private int compareAutocompletePlaces(
            MapPlace first,
            MapPlace second,
            String keyword,
            Double latitude,
            Double longitude
    ) {
        int scoreCompare = Integer.compare(
                autocompleteScore(second, keyword),
                autocompleteScore(first, keyword)
        );
        if (scoreCompare != 0) {
            return scoreCompare;
        }

        Double firstDistance = calculateDistanceMeters(latitude, longitude, first);
        Double secondDistance = calculateDistanceMeters(latitude, longitude, second);
        if (firstDistance != null || secondDistance != null) {
            if (firstDistance == null) {
                return 1;
            }
            if (secondDistance == null) {
                return -1;
            }
            int distanceCompare = Double.compare(firstDistance, secondDistance);
            if (distanceCompare != 0) {
                return distanceCompare;
            }
        }

        int nameCompare = first.getName().compareToIgnoreCase(second.getName());
        if (nameCompare != 0) {
            return nameCompare;
        }
        int addressCompare = first.getAddress().compareToIgnoreCase(second.getAddress());
        if (addressCompare != 0) {
            return addressCompare;
        }
        return Long.compare(first.getId(), second.getId());
    }

    private int autocompleteScore(MapPlace mapPlace, String keyword) {
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        String name = mapPlace.getName().toLowerCase(Locale.ROOT);
        String englishName = mapPlace.getEnglishName() == null
                ? ""
                : mapPlace.getEnglishName().toLowerCase(Locale.ROOT);
        String address = mapPlace.getAddress().toLowerCase(Locale.ROOT);
        String roadAddress = normalizedLowercase(mapPlace.getRoadAddress());
        String jibunAddress = normalizedLowercase(mapPlace.getJibunAddress());
        String category = mapPlace.getCategory() == null ? "" : mapPlace.getCategory().toLowerCase(Locale.ROOT);

        if (name.equals(normalizedKeyword)) {
            return 600;
        }
        if (englishName.equals(normalizedKeyword)) {
            return 550;
        }
        if (name.startsWith(normalizedKeyword)) {
            return 500;
        }
        if (englishName.startsWith(normalizedKeyword)) {
            return 450;
        }
        if (name.contains(normalizedKeyword)) {
            return 400;
        }
        if (englishName.contains(normalizedKeyword)) {
            return 350;
        }
        if (address.contains(normalizedKeyword)
                || roadAddress.contains(normalizedKeyword)
                || jibunAddress.contains(normalizedKeyword)) {
            return 200;
        }
        if (category.contains(normalizedKeyword)) {
            return 100;
        }
        return 0;
    }

    private String normalizedLowercase(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private Map<Long, Set<TouristCategory>> loadTouristCategories(List<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Set<TouristCategory>> categoriesByPlaceId = new HashMap<>();
        for (PlaceTouristCategoryProjection projection
                : placeSearchQueryRepository.findTouristCategoriesByPlaceIds(placeIds)) {
            categoriesByPlaceId
                    .computeIfAbsent(
                            projection.getPlaceId(),
                            ignored -> EnumSet.noneOf(TouristCategory.class)
                    )
                    .add(projection.getTouristCategory());
        }
        return categoriesByPlaceId;
    }

    private Double calculateDistanceMeters(Double latitude, Double longitude, MapPlace mapPlace) {
        if (latitude == null || longitude == null
                || mapPlace.getLatitude() == null
                || mapPlace.getLongitude() == null) {
            return null;
        }

        double latitude1 = Math.toRadians(latitude);
        double longitude1 = Math.toRadians(longitude);
        double latitude2 = Math.toRadians(mapPlace.getLatitude());
        double longitude2 = Math.toRadians(mapPlace.getLongitude());

        double deltaLatitude = latitude2 - latitude1;
        double deltaLongitude = longitude2 - longitude1;
        double a = Math.sin(deltaLatitude / 2) * Math.sin(deltaLatitude / 2)
                + Math.cos(latitude1) * Math.cos(latitude2)
                * Math.sin(deltaLongitude / 2) * Math.sin(deltaLongitude / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private record LocationSearch(
            boolean enabled,
            Double latitude,
            Double longitude,
            Double radiusMeters,
            Double minLatitude,
            Double maxLatitude,
            Double westLongitude,
            Double eastLongitude,
            boolean longitudeWrapped
    ) {

        static LocationSearch from(Double latitude, Double longitude, Double radiusKm) {
            boolean hasLatitude = latitude != null;
            boolean hasLongitude = longitude != null;
            boolean hasRadius = radiusKm != null;
            if (hasLatitude != hasLongitude || hasLatitude != hasRadius) {
                throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
            }
            if (!hasLatitude) {
                return new LocationSearch(false, null, null, null, null, null, null, null, false);
            }
            if (!Double.isFinite(latitude)
                    || !Double.isFinite(longitude)
                    || !Double.isFinite(radiusKm)
                    || radiusKm <= 0d) {
                throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
            }

            double latitudeDelta = radiusKm / KM_PER_LATITUDE_DEGREE;
            double minLatitude = Math.max(-90d, latitude - latitudeDelta);
            double maxLatitude = Math.min(90d, latitude + latitudeDelta);
            double longitudeDelta = toLongitudeDelta(latitude, radiusKm);
            LongitudeBounds longitudeBounds = LongitudeBounds.from(longitude, longitudeDelta);

            return new LocationSearch(
                    true,
                    latitude,
                    longitude,
                    radiusKm * 1_000d,
                    minLatitude,
                    maxLatitude,
                    longitudeBounds.westLongitude(),
                    longitudeBounds.eastLongitude(),
                    longitudeBounds.wrapped()
            );
        }

        private static double toLongitudeDelta(double latitude, double radiusKm) {
            double cosine = Math.cos(Math.toRadians(latitude));
            if (Math.abs(cosine) < MIN_COSINE_FOR_LONGITUDE_DELTA) {
                return 180d;
            }
            return Math.min(180d, radiusKm / (KM_PER_LATITUDE_DEGREE * Math.abs(cosine)));
        }
    }

    private record LongitudeBounds(double westLongitude, double eastLongitude, boolean wrapped) {

        static LongitudeBounds from(double longitude, double longitudeDelta) {
            if (longitudeDelta >= 180d) {
                return new LongitudeBounds(-180d, 180d, false);
            }

            double westLongitude = normalizeLongitude(longitude - longitudeDelta);
            double eastLongitude = normalizeLongitude(longitude + longitudeDelta);
            return new LongitudeBounds(westLongitude, eastLongitude, westLongitude > eastLongitude);
        }

        private static double normalizeLongitude(double longitude) {
            if (!Double.isFinite(longitude)) {
                throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
            }

            double normalized = (longitude + 180d) % 360d;
            if (normalized < 0d) {
                normalized += 360d;
            }
            double shifted = normalized - 180d;
            return shifted == -180d && longitude > 0d ? 180d : shifted;
        }
    }

}
