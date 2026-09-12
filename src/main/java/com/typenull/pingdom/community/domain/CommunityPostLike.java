package com.typenull.pingdom.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "community_post_like", uniqueConstraints = @UniqueConstraint(
        name = "uk_community_post_like_post_user", columnNames = {"community_post_id", "user_id"}
))
public class CommunityPostLike {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_post_like_id")
    private Long id;

    @Column(name = "community_post_id", nullable = false)
    private Long communityPostId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
