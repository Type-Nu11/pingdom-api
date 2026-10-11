package com.typenull.pingdom.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "community_comment_like", uniqueConstraints = @UniqueConstraint(
        name = "uk_community_comment_like_comment_user", columnNames = {"community_post_comment_id", "user_id"}
))
public class CommunityCommentLike {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_comment_like_id")
    private Long id;

    @Column(name = "community_post_comment_id", nullable = false)
    private Long communityPostCommentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

}
