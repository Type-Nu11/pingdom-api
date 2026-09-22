package com.typenull.pingdom.place.infrastructure.persistence.recommendation;

import com.typenull.pingdom.place.domain.recommendation.policy.PlaceRecommendationTrafficPolicy;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * 런타임 정책 전체를 버전 이름순으로 잠가 배분 비율을 함께 변경할 때 사용.
 * PESSIMISTIC_WRITE는 조회된 기존 행에 적용되며 빈 정책 테이블의 동시 초기화 방지는 보장 범위에서 제외.
 */
public interface PlaceRecommendationTrafficPolicyRepository extends JpaRepository<PlaceRecommendationTrafficPolicy, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT policy FROM PlaceRecommendationTrafficPolicy policy ORDER BY policy.recommendationVersion")
    List<PlaceRecommendationTrafficPolicy> findAllForUpdate();
}
