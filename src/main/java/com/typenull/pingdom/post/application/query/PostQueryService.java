package com.typenull.pingdom.post.application.query;

import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.post.api.dto.post.PostDetailResponse;
import com.typenull.pingdom.post.api.dto.post.PostListResponse;

public interface PostQueryService {

    PostListResponse listPosts(int page, int limit, Long userId);

    PostListResponse listMyPosts(int page, int limit, Long userId, SortParam sortParam, String keyword);

    PostListResponse listBookmarkedPosts(int page, int limit, Long userId);

    PostListResponse listLikedPosts(int page, int limit, Long userId);

    PostDetailResponse getPost(Long postId, Long userId);
}
