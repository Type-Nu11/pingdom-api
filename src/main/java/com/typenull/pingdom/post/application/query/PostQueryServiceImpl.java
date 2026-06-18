package com.typenull.pingdom.post.application.query;

import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.post.api.dto.post.PostDetailResponse;
import com.typenull.pingdom.post.api.dto.post.PostListItem;
import com.typenull.pingdom.post.api.dto.post.PostListResponse;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.List;
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

    @Override
    @Transactional(readOnly = true)
    public PostListResponse listPosts(int page, int limit, Long userId) {
        int safePage = Math.max(page, MIN_PAGE);
        int safeLimit = Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));

        Page<MapImage> imagePage = mapImageRepository.findAllBy(
                PageRequest.of(safePage - MIN_PAGE, safeLimit, latestFirstSort())
        );

        List<PostListItem> posts = imagePage.getContent()
                .stream()
                .map(mapImage -> toListItem(mapImage, userId))
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
        return new PostDetailResponse(
                mapImage.getId(),
                mapImage.getTitle(),
                mapImage.getImageUrl(),
                mapImage.getDescription(),
                mapImage.getUserId(),
                mapImage.getUsername(),
                mapImage.getCreatedAt(),
                mapImage.getLikeCount(),
                mapImageLikeRepository.existsByUserIdAndMapImageId(userId, mapImage.getId()),
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

    private PostListItem toListItem(MapImage mapImage, Long userId) {
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
                mapImageLikeRepository.existsByUserIdAndMapImageId(userId, mapImage.getId()),
                mapPlace != null ? mapPlace.getId() : null,
                mapPlace != null ? mapPlace.getName() : null
        );
    }
}
