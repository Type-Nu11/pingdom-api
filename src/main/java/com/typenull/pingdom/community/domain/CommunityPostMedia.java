package com.typenull.pingdom.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 게시글 작성 전 임시 사진과 연결 순서를 보관. 소유권·만료 검증은 잠금을 획득한 서비스가 수행. */
@Entity
@Getter
@Table(name = "community_post_media")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPostMedia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_post_media_id") private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "s3_key", nullable = false, unique = true, length = 500) private String s3Key;
    @Column(name = "image_url", nullable = false, length = 2048) private String imageUrl;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "file_size", nullable = false) private long fileSize;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CommunityPostMediaStatus status;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "community_post_id") private CommunityPost communityPost;
    @Column(name = "display_order") private Integer displayOrder;
    @Column(name = "connected_at") private LocalDateTime connectedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    public static CommunityPostMedia upload(Long userId, String key, String url, String contentType,
                                           long fileSize, LocalDateTime now) {
        CommunityPostMedia media = new CommunityPostMedia();
        media.userId = Objects.requireNonNull(userId);
        media.s3Key = Objects.requireNonNull(key);
        media.imageUrl = Objects.requireNonNull(url);
        media.contentType = Objects.requireNonNull(contentType);
        media.fileSize = fileSize;
        media.status = CommunityPostMediaStatus.UPLOADED;
        media.expiresAt = now.plusHours(24);
        media.createdAt = now;
        return media;
    }

    public boolean isOwnedBy(long userId) { return this.userId.equals(userId); }
    public boolean isExpiredAt(LocalDateTime now) { return !expiresAt.isAfter(now); }

    public void connect(CommunityPost post, int order, LocalDateTime now) {
        if (status != CommunityPostMediaStatus.UPLOADED) {
            throw new IllegalStateException("이미 연결된 게시글 사진입니다.");
        }
        communityPost = Objects.requireNonNull(post);
        displayOrder = order;
        connectedAt = now;
        status = CommunityPostMediaStatus.CONNECTED;
    }
}
