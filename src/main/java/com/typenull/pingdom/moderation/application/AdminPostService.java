package com.typenull.pingdom.moderation.application;

public interface AdminPostService {
    void deletePost(Long postId, Long adminUserId);
}
