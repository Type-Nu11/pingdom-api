package com.typenull.pingdom.community.application;

import com.typenull.pingdom.community.api.dto.CommunityPostCategoryListResponse;
import com.typenull.pingdom.community.domain.CommunityPostCategory;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityPostCategoryQueryService {

    @Transactional(readOnly = true)
    public CommunityPostCategoryListResponse findCategories() {
        List<CommunityPostCategoryListResponse.Item> categories = CommunityPostCategory.enabledCategories().stream()
                .map(category -> new CommunityPostCategoryListResponse.Item(
                        category.getId(),
                        category.getDisplayName()
                ))
                .toList();

        return new CommunityPostCategoryListResponse(categories);
    }
}
