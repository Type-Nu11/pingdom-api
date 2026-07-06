package com.typenull.pingdom.moderation.application.query;

import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.moderation.domain.AdminPostReviewStatus;
import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.moderation.api.dto.post.AdminPostItem;
import com.typenull.pingdom.moderation.api.dto.post.AdminPostResponse;

public interface AdminPostQueryService {
    AdminPostResponse listPosts(
            int page,
            int limit,
            SortParam sortParam,
            String keyword,
            AdminPostReviewStatus reviewStatus,
            PostReportStatus reportStatus
    );
    AdminPostItem getPost(Long postId);
}
