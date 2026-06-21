package com.typenull.pingdom.post.application.query;

import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.post.api.dto.post.PostDetailResponse;
import com.typenull.pingdom.post.api.dto.post.PostListItem;
import com.typenull.pingdom.post.api.dto.post.PostListResponse;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostQueryServiceImpl implements PostQueryService {

    private static final int MIN_PAGE = 1;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final MapImageRepository mapImageRepository;
    private final PlaceGrowthService placeGrowthService;
    private final MapImageLikeRepository mapImageLikeRepository;
    private final MapBookmarkRepository mapBookmarkRepository;

    @Override
    @Transactional(readOnly = true)
    public PostListResponse listPosts(int page, int limit, Long userId) {
        int safePage = Math.max(page, MIN_PAGE);
        int safeLimit = Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));

        Page<MapImage> imagePage = mapImageRepository.findAllBy(
                PageRequest.of(safePage - MIN_PAGE, safeLimit, latestFirstSort())
        );

        List<MapImage> mapImages = imagePage.getContent();
        List<Long> mapImageIds = mapImages.stream().map(MapImage::getId).toList();
        Set<Long> likedImageIds = (userId != null && !mapImageIds.isEmpty())
                ? mapImageLikeRepository.findLikedMapImageIdsByUserIdAndMapImageIds(userId, mapImageIds)
                : java.util.Collections.emptySet();
        List<Long> placeIds = mapImages.stream()
                .map(MapImage::getMapPlace)
                .filter(java.util.Objects::nonNull)
                .map(MapPlace::getId)
                .toList();
        Set<Long> bookmarkedPlaceIds = (userId != null && !placeIds.isEmpty())
                ? mapBookmarkRepository.findPlaceIdsByUserIdAndPlaceIds(userId, placeIds)
                : java.util.Collections.emptySet();

        List<PostListItem> posts = mapImages.stream()
                .map(mapImage -> toListItem(
                        mapImage,
                        likedImageIds.contains(mapImage.getId()),
                        isBookmarked(mapImage, bookmarkedPlaceIds)
                ))
                .toList();

        return PostListResponse.of(
                posts,
                safePage,
                safeLimit,
                imagePage.getTotalElements(),
                imagePage.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PostListResponse listBookmarkedPosts(int page, int limit, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }

        int safePage = Math.max(page, MIN_PAGE);
        int safeLimit = Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));

        Page<MapImage> imagePage = mapImageRepository.findBookmarkedByUserId(
                userId,
                PageRequest.of(safePage - MIN_PAGE, safeLimit)
        );
        List<MapImage> mapImages = imagePage.getContent();
        List<Long> mapImageIds = mapImages.stream().map(MapImage::getId).toList();
        Set<Long> likedImageIds = mapImageIds.isEmpty()
                ? java.util.Collections.emptySet()
                : mapImageLikeRepository.findLikedMapImageIdsByUserIdAndMapImageIds(userId, mapImageIds);

        List<PostListItem> posts = mapImages.stream()
                .map(mapImage -> toListItem(mapImage, likedImageIds.contains(mapImage.getId()), true))
                .toList();

        return PostListResponse.of(
                posts,
                safePage,
                safeLimit,
                imagePage.getTotalElements(),
                imagePage.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PostDetailResponse getPost(Long postId, Long userId) {
        MapImage mapImage = mapImageRepository.findWithMapPlaceById(postId)
                .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));

        MapPlace mapPlace = mapImage.getMapPlace();
        boolean liked = userId != null
                && mapImageLikeRepository.existsByUserIdAndMapImageId(
                userId,
                mapImage.getId()
        );

        return new PostDetailResponse(
                mapImage.getId(),
                mapImage.getTitle(),
                mapImage.getImageUrl(),
                mapImage.getDescription(),
                mapImage.getUserId(),
                mapImage.getUsername(),
                mapImage.getCreatedAt(),
                mapImage.getLikeCount(),
                liked,
                mapPlace != null ? mapPlace.getId() : null,
                mapPlace != null ? mapPlace.getName() : null,
                mapPlace != null ? mapPlace.getAddress() : null,
                mapPlace != null ? mapPlace.getLatitude() : null,
                mapPlace != null ? mapPlace.getLongitude() : null,
                placeGrowthService.snapshot(mapPlace)
        );
    }

    private Sort latestFirstSort() {
        return Sort.by(Sort.Order.desc("id"));
    }

    private PostListItem toListItem(MapImage mapImage, boolean liked, boolean bookmarked) {
        MapPlace mapPlace = mapImage.getMapPlace();
        return new PostListItem(
                mapImage.getId(),
                mapImage.getTitle(),
                mapImage.getImageUrl(),
                mapImage.getDescription(),
                mapImage.getUserId(),
                mapImage.getUsername(),
                mapImage.getCreatedAt(),
                mapImage.getLikeCount(),
                liked,
                bookmarked,
                mapPlace != null ? mapPlace.getId() : null,
                mapPlace != null ? mapPlace.getName() : null
        );
    }

    private boolean isBookmarked(MapImage mapImage, Set<Long> bookmarkedPlaceIds) {
        return mapImage.getMapPlace() != null
                && bookmarkedPlaceIds.contains(mapImage.getMapPlace().getId());
    }
}
