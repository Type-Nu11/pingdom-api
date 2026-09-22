package com.typenull.pingdom.place.infrastructure.persistence.recommendation;

import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceRecommendationSnapshot;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 장소 전체 집계 스냅샷과 합계를 읽음.
 * 명시적 잠금 메서드는 기존 행에 PESSIMISTIC_READ를 적용하며 없는 행 생성과 쓰기 직렬화는 보장 범위에서 제외.
 * 전체 SUM 결과는 행이 없으면 null일 수 있어 서비스가 0으로 보완.
 */
public interface PlaceRecommendationSnapshotRepository extends JpaRepository<PlaceRecommendationSnapshot, Long> {
    List<PlaceRecommendationSnapshot> findByPlaceIdIn(Collection<Long> placeIds);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT s
            FROM PlaceRecommendationSnapshot s
            WHERE s.placeId = :placeId
            """)
    Optional<PlaceRecommendationSnapshot> findByPlaceIdForReadLock(@Param("placeId") Long placeId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT s
            FROM PlaceRecommendationSnapshot s
            WHERE s.placeId IN :placeIds
            """)
    List<PlaceRecommendationSnapshot> findByPlaceIdInForReadLock(@Param("placeIds") Collection<Long> placeIds);

    Page<PlaceRecommendationSnapshot> findByUpdatedAtGreaterThanEqual(LocalDateTime updatedAt, Pageable pageable);

    @Query("""
            SELECT SUM(s.clickCount)
            FROM PlaceRecommendationSnapshot s
            """)
    Long sumClickCount();

    @Query("""
            SELECT SUM(s.exposureCount)
            FROM PlaceRecommendationSnapshot s
            """)
    Long sumExposureCount();
}
