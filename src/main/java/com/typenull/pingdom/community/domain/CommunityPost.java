package com.typenull.pingdom.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 커뮤니티 게시글의 식별자 집합이다.
 *
 * <p>장소 연결은 {@link CommunityPostPlace}가 별도로 관리한다.</p>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "community_post")
public class CommunityPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_post_id")
    private Long id;

    @Column(name = "category_id", nullable = false, length = 30)
    private String categoryId;

    @Column(name = "title", nullable = false, length = 50)
    private String title;

    @Column(name = "content", nullable = false, length = 5000)
    private String content;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Column(name = "hidden_by_admin_user_id")
    private Long hiddenByAdminUserId;

    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private CommunityPost(String categoryId, String title, String content, Long userId) {
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
    }

    public static CommunityPost create(String categoryId, String title, String content, Long userId) {
        return new CommunityPost(categoryId, title, content, userId);
    }

    public void hide(Long adminUserId, LocalDateTime hiddenAt) {
        if (!hidden) {
            this.hidden = true;
            this.hiddenByAdminUserId = Objects.requireNonNull(adminUserId, "adminUserId must not be null");
            this.hiddenAt = Objects.requireNonNull(hiddenAt, "hiddenAt must not be null");
        }
    }
}
