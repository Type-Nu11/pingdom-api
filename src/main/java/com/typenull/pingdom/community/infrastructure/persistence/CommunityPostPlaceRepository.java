package com.typenull.pingdom.community.infrastructure.persistence;

import com.typenull.pingdom.community.domain.CommunityPostPlace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityPostPlaceRepository extends JpaRepository<CommunityPostPlace, Long> {

    boolean existsByCommunityPost_IdAndMapPlace_Id(Long communityPostId, Long mapPlaceId);
}
