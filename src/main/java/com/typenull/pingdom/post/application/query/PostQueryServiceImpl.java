package com.typenull.pingdom.post.application.query;

import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.post.api.dto.PostDetailResponse;
import com.typenull.pingdom.post.api.dto.PostListItem;
import com.typenull.pingdom.post.api.dto.PostListResponse;
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

    private final MapImageRepository mapImageRepository;

    @Override
    @Transactional(readOnly = true)
    public PostListResponse listPosts(int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));

        Page<MapImage> imagePage = mapImageRepository.findAllBy(
                PageRequest.of(safePage - 1, safeLimit, latestFirstSort())
        );

        List<PostListItem> posts = imagePage.getContent()
                .stream()
                .map(this::toListItem)
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
    public PostDetailResponse getPost(Long postId) {
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
                mapPlace != null ? mapPlace.getId() : null,
                mapPlace != null ? mapPlace.getName() : null,
                mapPlace != null ? mapPlace.getAddress() : null,
                mapPlace != null ? mapPlace.getLatitude() : null,
                mapPlace != null ? mapPlace.getLongitude() : null
        );
    }

    private Sort latestFirstSort() {
        return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    }

    private PostListItem toListItem(MapImage mapImage) {
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
                mapPlace != null ? mapPlace.getId() : null,
                mapPlace != null ? mapPlace.getName() : null
        );
    }
}
