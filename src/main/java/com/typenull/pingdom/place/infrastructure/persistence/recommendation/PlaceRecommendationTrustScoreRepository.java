package com.typenull.pingdom.place.infrastructure.persistence.recommendation;

import com.typenull.pingdom.verification.domain.VisitorVerificationReport;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
