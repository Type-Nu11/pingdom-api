package com.typenull.pingdom.community.infrastructure.persistence;

import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.api.dto.CommunityPostListResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

    /**
     * 목록 화면에는 본문과 연결 장소를 포함하지 않고 식별자와 제목만 조회한다.
     */
    @Query("""
            select new com.typenull.pingdom.community.api.dto.CommunityPostListResponse$Item(post.id, post.title)
            from CommunityPost post
            where post.categoryId = :categoryId
            order by post.createdAt desc, post.id desc
            """)
    Page<CommunityPostListResponse.Item> findListItemsByCategoryId(
            @Param("categoryId") String categoryId,
            Pageable pageable
    );
}
