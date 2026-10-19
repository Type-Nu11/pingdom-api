package com.typenull.pingdom.community.application;

import com.typenull.pingdom.community.api.dto.CommunityPostCreateRequest;
import com.typenull.pingdom.community.api.dto.CommunityPostCreateResponse;
import com.typenull.pingdom.community.api.dto.CommunityPostUpdateRequest;
import com.typenull.pingdom.community.api.dto.CommunityPostUpdateResponse;
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
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 활성 카테고리와 연결 장소를 검증한 뒤 게시글 및 장소 연결을 한 트랜잭션으로 저장.
 * 스팟(PLACE) 카테고리는 한 곳 이상을 요구하며, 중복 ID를 조용히 제거하지 않고 입력 오류로 거절.
 */
@Service
@RequiredArgsConstructor
public class CommunityPostCommandService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostPlaceRepository communityPostPlaceRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final CommunityPostImageService imageService;
    private final Clock clock;

    /**
     * 활성 카테고리와 연결 장소를 검증한 뒤 제목·본문의 앞뒤 공백을 제거해 게시글과 장소 연결을 저장하고 ID를 반환.
     * 중복·없는 장소를 거절하며 스팟(PLACE) 카테고리에는 하나 이상의 장소가 필요.
     */
    @Transactional
    public CommunityPostCreateResponse create(long userId, CommunityPostCreateRequest request) {
        CommunityPostCategory category = CommunityPostCategory.findEnabledById(request.categoryId())
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.INVALID_CATEGORY));
        CommunityCountryCodes.validate(request.countryCode());
        List<Long> placeIds = normalizePlaceIds(request.placeIds());
        validatePlaceSelection(category, placeIds);
        List<MapPlace> places = findPlaces(placeIds);

        CommunityPost communityPost = communityPostRepository.save(CommunityPost.create(
                category.getId(),
                request.title().trim(),
                request.content().trim(),
                userId
        ));
        communityPost.describeLocation(request.countryCode(),
                request.region() == null || request.region().isBlank() ? null : request.region().trim());
        imageService.attach(userId, communityPost.getId(), request.imageIds());
        communityPostPlaceRepository.saveAll(places.stream()
                .map(place -> CommunityPostPlace.connect(communityPost, place))
                .toList());

        return new CommunityPostCreateResponse(communityPost.getId(), placeIds);
    }

    @Transactional
    public CommunityPostUpdateResponse update(long userId, long postId, CommunityPostUpdateRequest request) {
        CommunityPost post = requireOwnPost(userId, postId, false);
        String categoryId = request.getCategoryId() == null ? post.getCategoryId() : request.getCategoryId();
        CommunityPostCategory category = CommunityPostCategory.findEnabledById(categoryId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.INVALID_CATEGORY));
        if (request.isCountryCodeSpecified()) CommunityCountryCodes.validate(request.getCountryCode());

        List<Long> placeIds = request.getPlaceIds() == null
                ? communityPostPlaceRepository.findAllWithMapPlaceByCommunityPostId(postId).stream()
                        .map(CommunityPostPlace::getMapPlaceId).toList()
                : normalizePlaceIds(request.getPlaceIds());
        validatePlaceSelection(category, placeIds);
        List<MapPlace> replacementPlaces = request.getPlaceIds() == null ? List.of() : findPlaces(placeIds);

        // 게시글 잠금 후 모든 이미지 행을 ID 순서로 잠가 사진 교체·삭제가 부분 적용되지 않게 한다.
        if (request.getImageIds() != null) imageService.replace(userId, postId, request.getImageIds());
        if (request.getPlaceIds() != null) {
            communityPostPlaceRepository.deleteAllByPostId(postId);
            communityPostPlaceRepository.saveAll(replacementPlaces.stream()
                    .map(place -> CommunityPostPlace.connect(post, place)).toList());
        }
        post.describeLocation(request.isCountryCodeSpecified() ? request.getCountryCode() : post.getCountryCode(),
                request.isRegionSpecified() ? normalizeRegion(request.getRegion()) : post.getRegion());
        LocalDateTime updatedAt = LocalDateTime.now(clock);
        post.update(categoryId, request.getTitle() == null ? post.getTitle() : request.getTitle().trim(),
                request.getContent() == null ? post.getContent() : request.getContent().trim(), updatedAt);
        return new CommunityPostUpdateResponse(postId, updatedAt);
    }

    @Transactional
    public void delete(long userId, long postId) {
        CommunityPost post = requireOwnPost(userId, postId, true);
        imageService.deleteForPost(postId);
        communityPostPlaceRepository.deleteAllByPostId(postId);
        post.delete(LocalDateTime.now(clock));
    }

    private CommunityPost requireOwnPost(long userId, long postId, boolean allowHidden) {
        return communityPostRepository.findByIdForUpdate(postId)
                .filter(post -> post.getUserId().equals(userId) && !post.isDeleted()
                        && (allowHidden || !post.isHidden()))
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.POST_NOT_FOUND));
    }

    private String normalizeRegion(String region) {
        return region == null || region.isBlank() ? null : region.trim();
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

    /**
     * 연결 요청의 모든 장소가 존재하는지 건수로 대조. 장소의 운영·탐색 노출 상태는 이 단계의 검사 범위에서 제외.
     */
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
