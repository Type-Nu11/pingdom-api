package com.typenull.pingdom.community.application;

import com.typenull.pingdom.community.api.dto.CommunityPostCreateRequest;
import com.typenull.pingdom.community.api.dto.CommunityPostCreateResponse;
import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostCategory;
import com.typenull.pingdom.community.domain.CommunityPostPlace;
import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostPlaceRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityPostCommandService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostPlaceRepository communityPostPlaceRepository;
    private final MapPlaceRepository mapPlaceRepository;

    @Transactional
    public CommunityPostCreateResponse create(long userId, CommunityPostCreateRequest request) {
        CommunityPostCategory category = CommunityPostCategory.findEnabledById(request.categoryId())
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.INVALID_CATEGORY));
        List<Long> placeIds = normalizePlaceIds(request.placeIds());
        validatePlaceSelection(category, placeIds);
        List<MapPlace> places = findPlaces(placeIds);

        CommunityPost communityPost = communityPostRepository.save(CommunityPost.create(
                category.getId(),
                request.title().trim(),
                request.content().trim(),
                userId
        ));
        communityPostPlaceRepository.saveAll(places.stream()
                .map(place -> CommunityPostPlace.connect(communityPost, place))
                .toList());

        return new CommunityPostCreateResponse(communityPost.getId(), placeIds);
    }

    private List<Long> normalizePlaceIds(List<Long> requestedPlaceIds) {
        if (requestedPlaceIds == null || requestedPlaceIds.isEmpty()) {
            return List.of();
        }
        Set<Long> distinctPlaceIds = new LinkedHashSet<>(requestedPlaceIds);
        if (distinctPlaceIds.size() != requestedPlaceIds.size()) {
            throw new CommunityException(CommunityErrorCode.DUPLICATE_PLACE);
        }
        return List.copyOf(distinctPlaceIds);
    }

    private void validatePlaceSelection(CommunityPostCategory category, List<Long> placeIds) {
        if (category == CommunityPostCategory.PLACE && placeIds.isEmpty()) {
            throw new CommunityException(CommunityErrorCode.PLACE_REQUIRED);
        }
    }

    private List<MapPlace> findPlaces(List<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return List.of();
        }
        List<MapPlace> places = mapPlaceRepository.findAllById(placeIds);
        if (places.size() != placeIds.size()) {
            throw new CommunityException(CommunityErrorCode.PLACE_NOT_FOUND);
        }
        return places;
    }
}
