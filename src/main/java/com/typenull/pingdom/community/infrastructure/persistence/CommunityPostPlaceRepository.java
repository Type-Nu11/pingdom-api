package com.typenull.pingdom.community.infrastructure.persistence;

import com.typenull.pingdom.community.domain.CommunityPostPlace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityPostPlaceRepository extends JpaRepository<CommunityPostPlace, Long> {

    boolean existsByCommunityPost_IdAndMapPlace_Id(Long communityPostId, Long mapPlaceId);

    /** 게시글 상세에 필요한 연결 장소를 한 번에 조회해 N+1 조회를 방지한다. */
    @Query("""
            select communityPostPlace
            from CommunityPostPlace communityPostPlace
            left join fetch communityPostPlace.mapPlace
            where communityPostPlace.communityPost.id = :postId
            order by communityPostPlace.id asc
            """)
    List<CommunityPostPlace> findAllWithMapPlaceByCommunityPostId(@Param("postId") Long postId);
}
