package com.typenull.pingdom.engagement.infrastructure.persistence;

import com.typenull.pingdom.engagement.domain.PostReport;
import java.util.Collection;
import java.util.List;

import com.typenull.pingdom.engagement.domain.PostReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostReportRepository extends JpaRepository<PostReport, Long> {

    long countByStatus(PostReportStatus status);

    boolean existsByReporterUserIdAndMapImage_Id(Long reporterUserId, Long mapImageId);

    @EntityGraph(attributePaths = "mapImage")
    Page<PostReport> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"mapImage", "mapImage.mapPlace"})
    @Query("""
            SELECT pr
            FROM PostReport pr
            WHERE pr.status = :status
            """)
    List<PostReport> findRecentByStatus(@Param("status") PostReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"mapImage", "mapImage.mapPlace"})
    @Query("""
            SELECT pr
            FROM PostReport pr
            WHERE pr.status <> :status
            """)
    List<PostReport> findRecentByStatusNot(@Param("status") PostReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"mapImage", "mapImage.mapPlace"})
    Page<PostReport> findByReporterUserIdOrderByIdDesc(Long reporterUserId, Pageable pageable);

    List<PostReport> findAllByMapImage_IdAndStatusIn(Long mapImageId, Collection<com.typenull.pingdom.engagement.domain.PostReportStatus> statuses);

    List<PostReport> findAllByReportedImageIdAndStatusOrderByIdAsc(Long reportedImageId, PostReportStatus status);

    boolean existsByReportedUserIdAndStatusAndIdNot(
            Long reportedUserId,
            com.typenull.pingdom.engagement.domain.PostReportStatus status,
            Long id
    );

    boolean existsByReportedUserIdAndStatusAndReason(
            Long reportedUserId,
            com.typenull.pingdom.engagement.domain.PostReportStatus status,
            String reason
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PostReport pr set pr.mapImage = null where pr.mapImage.id = :mapImageId")
    int detachMapImageByMapImageId(@Param("mapImageId") Long mapImageId);

    Page<PostReport> findByStatus(PostReportStatus status, Pageable pageable);

    @Query(
            "select pr " +
                    "from PostReport pr " +
                    "where pr.status = :status " +
                    "and ( " +
                    "   lower(pr.reporterUsername) like lower(concat('%', :keyword, '%')) escape '\\' " +
                    "   or lower(pr.reason) like lower(concat('%', :keyword, '%')) escape '\\' " +
                    "   or (:numericKeyword is not null and pr.reportedUserId = :numericKeyword) " +
                    ")"
    )
    Page<PostReport> searchPendingReports(
            @Param("status") PostReportStatus status,
            @Param("keyword") String keyword,
            @Param("numericKeyword") Long numericKeyword,
            Pageable pageable
    );

    @Query(value = """
        SELECT CASE
                   WHEN COUNT(*) = 3
                    AND COUNT(CASE WHEN recent_reports.reason = :currentReason THEN 1 END) = 3
                   THEN true
                   ELSE false
               END
        FROM (
            SELECT reason
            FROM post_report
            WHERE reporter_user_id = :reporterUserId
              AND id < :currentReportId
            ORDER BY id DESC
            LIMIT 3
        ) recent_reports
        """, nativeQuery = true)
    boolean existsSameReasonInLatestThreeBeforeCurrent(
            @Param("reporterUserId") Long reporterUserId,
            @Param("currentReportId") Long currentReportId,
            @Param("currentReason") String currentReason
    );

    @Query(value = """
        SELECT CASE
                   WHEN COUNT(DISTINCT pr.reporter_user_id) >= 10 THEN true
                   ELSE false
               END
        FROM post_report pr
        WHERE pr.reported_image_id = :reportedImageId
        """, nativeQuery = true)
    boolean existsAtLeastTenDistinctReportersByReportedImageId(
            @Param("reportedImageId") Long reportedImageId
    );
}
