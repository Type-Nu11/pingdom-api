package com.typenull.pingdom.post.application.query;

import com.typenull.pingdom.post.api.dto.post.PostDetailResponse;
import com.typenull.pingdom.post.api.dto.post.PostListResponse;

public interface PostQueryService {

    PostListResponse listPosts(int page, int limit);

    PostDetailResponse getPost(Long postId);
}
