package com.typenull.pingdom.moderation.application.query;

import com.typenull.pingdom.domain.admin.enums.SortParam;
import com.typenull.pingdom.moderation.api.dto.post.AdminPostResponse;

public interface AdminPostQueryService {
    AdminPostResponse listPosts(int limit, int page, SortParam sortParam);
}
