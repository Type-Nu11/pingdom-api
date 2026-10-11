package com.typenull.pingdom.community.infrastructure.persistence;

import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.api.dto.CommunityPostListResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

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

    /**
     * 목록 화면에는 본문과 연결 장소를 포함하지 않고 식별자와 제목만 조회.
     */
    @Query("""
            select new com.typenull.pingdom.community.api.dto.CommunityPostListResponse$Item(post.id, post.title)
            from CommunityPost post
            where post.categoryId = :categoryId
              and post.hidden = false
            order by post.createdAt desc, post.id desc
            """)
    Page<CommunityPostListResponse.Item> findListItemsByCategoryId(
            @Param("categoryId") String categoryId,
            Pageable pageable
    );

    @org.springframework.data.jpa.repository.Modifying
    @Query("update CommunityPost post set post.viewCount = post.viewCount + 1 where post.id = :id and post.hidden = false")
    int incrementViewCount(@Param("id") long id);

    @Query("select new com.typenull.pingdom.community.api.dto.CommunityPostListResponse$Item(p.id,p.title) from CommunityPost p where p.hidden = false and (:category is null or p.categoryId = :category) and (:country is null or p.countryCode = :country)")
    Page<CommunityPostListResponse.Item> findListItems(@Param("category") String category,
            @Param("country") String country, Pageable pageable);

    /** 한 페이지의 미리보기·이미지·카운터를 일괄 조회해 항목 수만큼 추가 쿼리가 발생하지 않습니다. */
    @Query(value = """
        select p.community_post_id as "postId", left(p.content,150) as "contentPreview",
               p.country_code as "countryCode", p.region, p.created_at as "createdAt",
               p.view_count as "viewCount", p.category_id as "categoryId",
               (select i.image_url from community_post_image i where i.post_id=p.community_post_id order by i.position limit 1) as "imageUrl",
               (select count(*) from community_post_image i where i.post_id=p.community_post_id) as "imageCount",
               (select count(*) from community_post_like l where l.community_post_id=p.community_post_id) as "likeCount",
               (select count(*) from community_post_comment c where c.community_post_id=p.community_post_id and not c.hidden
                 and (c.parent_comment_id is null or exists(select 1 from community_post_comment parent where parent.community_post_comment_id=c.parent_comment_id and not parent.hidden))) as "commentCount"
        from community_post p where p.community_post_id in :ids and not p.hidden
        """, nativeQuery = true)
    java.util.List<Summary> findSummaries(@Param("ids") java.util.List<Long> ids);
    interface Summary {
        Long getPostId(); String getContentPreview(); String getImageUrl(); long getImageCount();
        String getCountryCode(); String getRegion(); java.time.LocalDateTime getCreatedAt();
        long getViewCount(); long getLikeCount(); long getCommentCount(); String getCategoryId();
    }
}
