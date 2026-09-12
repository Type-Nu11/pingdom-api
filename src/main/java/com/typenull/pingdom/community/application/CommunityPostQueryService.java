package com.typenull.pingdom.community.application;

import com.typenull.pingdom.community.api.dto.CommunityPostListResponse;
import com.typenull.pingdom.community.api.dto.CommunityPostDetailResponse;
import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostCategory;
import com.typenull.pingdom.community.domain.CommunityPostPlace;
import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostPlaceRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommunityPostQueryService {

    private static final String DELETED_PLACE_NAME = "삭제된 장소입니다";

    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostPlaceRepository communityPostPlaceRepository;

    @Transactional(readOnly = true)
    public CommunityPostListResponse findByCategory(String categoryId, int page, int limit) {
        CommunityPostCategory.findEnabledById(categoryId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.INVALID_CATEGORY));

        Page<CommunityPostListResponse.Item> result = communityPostRepository.findListItemsByCategoryId(
                categoryId,
                PageRequest.of(page - 1, limit, Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                ))
        );

        return new CommunityPostListResponse(
                result.getContent(),
                page,
                limit,
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public CommunityPostDetailResponse findDetail(long postId) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.POST_NOT_FOUND));
        List<CommunityPostDetailResponse.Place> places = communityPostPlaceRepository
                .findAllWithMapPlaceByCommunityPostId(postId)
                .stream()
                .map(this::toPlace)
                .toList();

        return new CommunityPostDetailResponse(post.getId(), post.getTitle(), post.getContent(), places);
    }

    private CommunityPostDetailResponse.Place toPlace(CommunityPostPlace postPlace) {
        if (postPlace.getMapPlace() == null) {
            return new CommunityPostDetailResponse.Place(postPlace.getMapPlaceId(), DELETED_PLACE_NAME, true);
        }
        return new CommunityPostDetailResponse.Place(
                postPlace.getMapPlace().getId(),
                postPlace.getMapPlace().getName(),
                false
        );
    }
}
