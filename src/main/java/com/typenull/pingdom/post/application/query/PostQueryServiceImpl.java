package com.typenull.pingdom.post.application.query;

import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.post.api.dto.post.PostDetailResponse;
import com.typenull.pingdom.post.api.dto.post.PostListItem;
import com.typenull.pingdom.post.api.dto.post.PostListResponse;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.domain.MapImageVisibilityStatus;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 게시글 목록·상세 조회와 좋아요·북마크 상태를 배치 조회 결과로 조합합니다. */
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

    /** 페이지 조건을 보정하고 공개 게시글에 사용자별 상호작용 상태를 결합합니다. */
    @Override
    @Transactional(readOnly = true)
    public PostListResponse listPosts(int page, int limit, Long userId) {
        int safePage = Math.max(page, MIN_PAGE);
        int safeLimit = Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));

        Page<MapImage> imagePage = mapImageRepository.findAllByVisibilityStatus(
                MapImageVisibilityStatus.ACTIVE,
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
                .distinct()
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

    /**
     * 작성자 본인의 게시글은 숨김 상태도 포함하며 제목·설명·장소명과 숫자 게시글 ID로 검색합니다.
     * 페이지는 1부터, 크기는 1~100으로 보정하고 정렬 동률은 ID로 안정화합니다.
     */
    @Override
    @Transactional(readOnly = true)
    public PostListResponse listMyPosts(int page, int limit, Long userId, SortParam sortParam, String keyword) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }

        int safePage = Math.max(page, MIN_PAGE);
        int safeLimit = Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));
        SortParam safeSortParam = sortParam == null ? SortParam.LATEST : sortParam;
        String safeKeyword = keyword == null ? "" : keyword.trim();
        Long numericKeyword = parseLongKeyword(safeKeyword);

        Page<MapImage> imagePage = mapImageRepository.searchMyPosts(
                userId,
                safeKeyword,
                numericKeyword,
                PageRequest.of(safePage - MIN_PAGE, safeLimit, toSort(safeSortParam))
        );
        List<MapImage> mapImages = imagePage.getContent();
        List<Long> mapImageIds = mapImages.stream().map(MapImage::getId).toList();
        Set<Long> likedImageIds = mapImageIds.isEmpty()
                ? java.util.Collections.emptySet()
                : mapImageLikeRepository.findLikedMapImageIdsByUserIdAndMapImageIds(userId, mapImageIds);
        List<Long> placeIds = mapImages.stream()
                .map(MapImage::getMapPlace)
                .filter(java.util.Objects::nonNull)
                .map(MapPlace::getId)
                .distinct()
                .toList();
        Set<Long> bookmarkedPlaceIds = placeIds.isEmpty()
                ? java.util.Collections.emptySet()
                : mapBookmarkRepository.findPlaceIdsByUserIdAndPlaceIds(userId, placeIds);

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

    /**
     * 북마크한 장소마다 공개 게시글 중 가장 큰 ID의 한 건을 골라 본인 좋아요 상태를 결합한다.
     * 사용자 ID는 필수이며 페이지·크기를 보정한다. 결과는 북마크 생성 시각·ID 내림차순이고 북마크 상태를 true로 반환한다.
     */
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

    /**
     * 본인이 좋아요한 공개 게시글을 좋아요 ID 내림차순으로 조회하고 연결 장소의 북마크 상태를 결합한다.
     * 사용자 ID가 없으면 거부하며, 페이지는 최소 1·크기는 1~100으로 보정한다.
     */
    @Override
    @Transactional(readOnly = true)
    public PostListResponse listLikedPosts(int page, int limit, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }

        int safePage = Math.max(page, MIN_PAGE);
        int safeLimit = Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));

        Page<MapImage> imagePage = mapImageRepository.findLikedByUserId(
                userId,
                PageRequest.of(safePage - MIN_PAGE, safeLimit)
        );
        List<MapImage> mapImages = imagePage.getContent();
        List<Long> placeIds = mapImages.stream()
                .map(MapImage::getMapPlace)
                .filter(java.util.Objects::nonNull)
                .map(MapPlace::getId)
                .distinct()
                .toList();
        Set<Long> bookmarkedPlaceIds = placeIds.isEmpty()
                ? java.util.Collections.emptySet()
                : mapBookmarkRepository.findPlaceIdsByUserIdAndPlaceIds(userId, placeIds);

        List<PostListItem> posts = mapImages.stream()
                .map(mapImage -> toListItem(mapImage, true, isBookmarked(mapImage, bookmarkedPlaceIds)))
                .toList();

        return PostListResponse.of(
                posts,
                safePage,
                safeLimit,
                imagePage.getTotalElements(),
                imagePage.getTotalPages()
        );
    }

    /** 숨긴 게시글은 작성자에게만 상세를 제공하고, 그 외 사용자에게는 존재하지 않는 게시글과 같은 오류를 반환합니다. */
    @Override
    @Transactional(readOnly = true)
    public PostDetailResponse getPost(Long postId, Long userId) {
        MapImage mapImage = mapImageRepository.findWithMapPlaceById(postId)
                .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));
        if (!mapImage.isVisible() && !Objects.equals(mapImage.getUserId(), userId)) {
            throw new MapException(MapErrorCode.IMAGE_NOT_FOUND);
        }

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
                mapImage.getThumbnailUrl(),
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

    private Sort toSort(SortParam sortParam) {
        return switch (sortParam) {
            case OLDEST -> Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
            case LATEST -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case MOST_LIKED -> Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        };
    }

    private Long parseLongKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(keyword);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private PostListItem toListItem(MapImage mapImage, boolean liked, boolean bookmarked) {
        MapPlace mapPlace = mapImage.getMapPlace();
        return new PostListItem(
                mapImage.getId(),
                mapImage.getTitle(),
                mapImage.getImageUrl(),
                mapImage.getThumbnailUrl(),
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
