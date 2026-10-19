package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.review.PlaceReviewRecommendReason;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 공개 리뷰 집계를 단일 SQL snapshot에서 계산하며 리뷰·사진 엔티티를 로딩하지 않습니다. */
@Repository
@RequiredArgsConstructor
public class PlaceReviewSummaryQueryRepository {

    // 단일 사유의 자유 문자열이 enum 저장소로 암묵 변환되지 않도록 CASE의 구조화 값을 문자열로 통일합니다.
    private static final String SUMMARY_SQL = """
            WITH review_tags AS (
                SELECT place.map_place_id, review.id AS review_id, review.user_id,
                       CASE WHEN selected.review_id IS NULL THEN review.recommend_reason
                            ELSE CAST(selected.reason AS VARCHAR(100)) END AS reason
                FROM map_place place
                LEFT JOIN place_review review
                    ON review.place_id = place.map_place_id AND review.visibility_status = 'VISIBLE'
                LEFT JOIN place_review_recommend_reason selected ON selected.review_id = review.id
                WHERE place.map_place_id = :placeId
            )
            SELECT COUNT(DISTINCT review_id) AS total_review_count,
                   COUNT(DISTINCT CASE WHEN reason IN (:supportedReasons) THEN user_id END) AS participant_count,
                   COUNT(DISTINCT CASE WHEN reason = 'FRIENDLY' THEN user_id END) AS FRIENDLY,
                   COUNT(DISTINCT CASE WHEN reason = 'EASY_TO_FIND' THEN user_id END) AS EASY_TO_FIND,
                   COUNT(DISTINCT CASE WHEN reason = 'GOOD_FOOD' THEN user_id END) AS GOOD_FOOD,
                   COUNT(DISTINCT CASE WHEN reason = 'MULTILINGUAL_SUPPORT' THEN user_id END) AS MULTILINGUAL_SUPPORT,
                   COUNT(DISTINCT CASE WHEN reason = 'PARKING' THEN user_id END) AS PARKING,
                   COUNT(DISTINCT CASE WHEN reason = 'PHOTO_SPOT' THEN user_id END) AS PHOTO_SPOT,
                   COUNT(DISTINCT CASE WHEN reason = 'CLEAN' THEN user_id END) AS CLEAN
            FROM review_tags
            GROUP BY map_place_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /** 복수 태그가 있으면 이전 단일 값으로 fallback하지 않으며, 알 수 없는 코드·작성자 누락은 인원에서 제외합니다. */
    public Optional<SummaryCounts> findByPlaceId(Long placeId) {
        return jdbcTemplate.query(SUMMARY_SQL, Map.of(
                "placeId", placeId,
                "supportedReasons", Arrays.stream(PlaceReviewRecommendReason.values()).map(Enum::name).toList()
        ), (resultSet, rowNumber) -> {
            Map<PlaceReviewRecommendReason, Long> reasonCounts = new EnumMap<>(PlaceReviewRecommendReason.class);
            for (PlaceReviewRecommendReason reason : PlaceReviewRecommendReason.values()) {
                reasonCounts.put(reason, resultSet.getLong(reason.name()));
            }
            return new SummaryCounts(
                    resultSet.getLong("total_review_count"),
                    resultSet.getLong("participant_count"),
                    reasonCounts
            );
        }).stream().findFirst();
    }

    public record SummaryCounts(
            long totalReviewCount,
            long participantCount,
            Map<PlaceReviewRecommendReason, Long> reasonCounts
    ) {
        public SummaryCounts {
            reasonCounts = Map.copyOf(reasonCounts);
        }
    }
}
