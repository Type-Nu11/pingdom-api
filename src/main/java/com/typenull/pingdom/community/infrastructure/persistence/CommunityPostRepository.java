package com.typenull.pingdom.community.infrastructure.persistence;

import com.typenull.pingdom.community.domain.CommunityPost;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

    Optional<CommunityPost> findByIdAndHiddenFalse(Long postId);

    @Query("""
            select post from CommunityPost post
            where (:categoryId is null or post.categoryId = :categoryId)
              and (:hidden is null or post.hidden = :hidden)
            """)
    Page<CommunityPost> findAllForAdmin(
            @Param("categoryId") String categoryId,
            @Param("hidden") Boolean hidden,
            Pageable pageable
    );

    boolean existsByIdAndHiddenFalse(Long postId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select post from CommunityPost post where post.id = :postId")
    Optional<CommunityPost> findByIdForUpdate(@Param("postId") Long postId);

    interface ListItem {
        Long getPostId();
        String getTitle();
        String getContent();
        String getThumbnailUrl();
        long getImageCount();
        String getRegion();
        LocalDateTime getCreatedAt();
        long getViewCount();
        long getLikeCount();
        long getCommentCount();
        String getCategoryId();
        String getCountry();
    }

    /** 각각의 집계를 독립 subquery로 조회해 이미지·좋아요·댓글 조인에 따른 곱집계를 방지. */
    @Query(value = """
            SELECT p.community_post_id AS "postId", p.title AS "title", p.content AS "content",
                   p.category_id AS "categoryId", p.created_at AS "createdAt", p.view_count AS "viewCount",
                   CASE WHEN u.withdrawn_at IS NULL THEN u.country END AS "country",
                   (SELECT m.image_url FROM community_post_media m WHERE m.community_post_id = p.community_post_id
                    ORDER BY m.display_order LIMIT 1) AS "thumbnailUrl",
                   (SELECT count(*) FROM community_post_media m WHERE m.community_post_id = p.community_post_id) AS "imageCount",
                   (SELECT count(*) FROM community_post_like l WHERE l.community_post_id = p.community_post_id) AS "likeCount",
                   (SELECT count(*) FROM community_post_comment c LEFT JOIN community_post_comment parent
                    ON parent.community_post_comment_id = c.parent_comment_id
                    WHERE c.community_post_id = p.community_post_id AND NOT c.hidden
                      AND (c.parent_comment_id IS NULL OR NOT parent.hidden)) AS "commentCount",
                   (SELECT coalesce(r.region_name, mp.address) FROM community_post_place pp
                    LEFT JOIN map_place mp ON mp.map_place_id = pp.map_place_id
                    LEFT JOIN place_administrative_region r ON r.region_code = mp.region_code
                    WHERE pp.community_post_id = p.community_post_id
                    ORDER BY pp.community_post_place_id LIMIT 1) AS "region"
            FROM community_post p LEFT JOIN users u ON u.id = p.user_id
            WHERE NOT p.hidden AND (CAST(:categoryId AS varchar) IS NULL OR p.category_id = :categoryId)
              AND (NOT :countryEnabled OR (u.withdrawn_at IS NULL AND lower(trim(u.country)) IN (:countryAliases)))
            ORDER BY p.created_at DESC, p.community_post_id DESC
            """, countQuery = """
            SELECT count(*) FROM community_post p LEFT JOIN users u ON u.id = p.user_id
            WHERE NOT p.hidden AND (CAST(:categoryId AS varchar) IS NULL OR p.category_id = :categoryId)
              AND (NOT :countryEnabled OR (u.withdrawn_at IS NULL AND lower(trim(u.country)) IN (:countryAliases)))
            """, nativeQuery = true)
    Page<ListItem> findListItems(@Param("categoryId") String categoryId,
                                 @Param("countryEnabled") boolean countryEnabled,
                                 @Param("countryAliases") List<String> countryAliases, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CommunityPost post set post.viewCount = post.viewCount + 1 where post.id = :postId")
    int increaseViewCount(@Param("postId") long postId);
}
