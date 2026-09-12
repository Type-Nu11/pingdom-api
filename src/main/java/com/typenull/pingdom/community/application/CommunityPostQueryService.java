package com.typenull.pingdom.community.application;

import com.typenull.pingdom.community.api.dto.CommunityPostListResponse;
import com.typenull.pingdom.community.domain.CommunityPostCategory;
import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityPostQueryService {

    private final CommunityPostRepository communityPostRepository;

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
}
