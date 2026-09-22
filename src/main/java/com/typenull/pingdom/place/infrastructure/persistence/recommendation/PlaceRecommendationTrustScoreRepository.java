package com.typenull.pingdom.place.infrastructure.persistence.recommendation;

import com.typenull.pingdom.verification.domain.VisitorVerificationReport;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 승인된 방문 제보의 신고자를 장소별로 중복 제거하여 신뢰도 평균을 구합니다.
 * 신고자 정책이 없으면 100점을 사용하고 50점짜리 가상 표본 3개를 더해 0~1 기준으로 완화합니다.
 * 승인 제보가 없는 장소는 행을 반환하지 않아 호출자의 기본값이 적용됩니다.
 */
public interface PlaceRecommendationTrustScoreRepository extends JpaRepository<VisitorVerificationReport, Long> {

    String TRUST_SCORE_QUERY = """
            SELECT reporter_score.place_id AS placeId,
                   (SUM(reporter_score.trust_score) + 150.0)
                       / (COUNT(*) + 3.0) / 100.0 AS trustScore
            FROM (
                SELECT DISTINCT report.place_id,
                                report.reporter_user_id,
                                COALESCE(policy.trust_score, 100) AS trust_score
                FROM visitor_verification_report report
                LEFT JOIN reporter_moderation_policy policy
                  ON policy.reporter_user_id = report.reporter_user_id
                WHERE report.status = 'ACCEPTED'
                  AND report.place_id IN (:placeIds)
            ) reporter_score
            GROUP BY reporter_score.place_id
            """;

    @Query(value = TRUST_SCORE_QUERY, nativeQuery = true)
    List<PlaceTrustScoreProjection> findTrustScoresByPlaceIds(@Param("placeIds") Collection<Long> placeIds);

    interface PlaceTrustScoreProjection {
        Long getPlaceId();

        Double getTrustScore();
    }
}
