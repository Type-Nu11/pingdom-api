package com.typenull.pingdom.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.CreationTimestamp;

/**
 * 게시글 댓글과 최초 관리자 숨김 처리자·시각 보관.
 * 이미 숨긴 댓글의 hide 재호출은 최초 처리 정보를 유지하며 숨김 복구 기능은 미제공.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "community_post_comment")
public class CommunityPostComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_post_comment_id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "community_post_id", nullable = false)
    private CommunityPost communityPost;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Column(name = "hidden_by_admin_user_id")
    private Long hiddenByAdminUserId;

    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private CommunityPostComment(CommunityPost communityPost, Long userId, String content) {
        this.communityPost = Objects.requireNonNull(communityPost, "communityPost must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
    }

    public static CommunityPostComment create(CommunityPost communityPost, Long userId, String content) {
        return new CommunityPostComment(communityPost, userId, content);
    }

    public void hide(Long adminUserId, LocalDateTime hiddenAt) {
        if (!hidden) {
            this.hidden = true;
            this.hiddenByAdminUserId = Objects.requireNonNull(adminUserId, "adminUserId must not be null");
            this.hiddenAt = Objects.requireNonNull(hiddenAt, "hiddenAt must not be null");
        }
    }
}
