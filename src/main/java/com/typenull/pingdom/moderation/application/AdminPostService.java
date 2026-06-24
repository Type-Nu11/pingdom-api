package com.typenull.pingdom.moderation.application;

public interface AdminPostService {
    void deletePost(Long postId, Long adminUserId);

    void hidePost(Long postId, String reason, Long adminUserId);

    void restorePost(Long postId, String reason, Long adminUserId);
}
