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

    boolean existsByReporterUserIdAndMapImage_Id(Long reporterUserId, Long mapImageId);

    @EntityGraph(attributePaths = "mapImage")
    Page<PostReport> findAllBy(Pageable pageable);

    List<PostReport> findAllByMapImage_IdInOrderByIdDesc(Collection<Long> mapImageIds);

    List<PostReport> findAllByMapImage_IdAndStatusIn(Long mapImageId, Collection<com.typenull.pingdom.engagement.domain.PostReportStatus> statuses);

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

    @Query("""
        select pr
        from PostReport pr
        where pr.status = :status
          and (
                lower(pr.reporterUsername) like lower(concat('%', :keyword, '%'))
                or lower(pr.reason) like lower(concat('%', :keyword, '%'))
                or (:numericKeyword is not null and pr.reportedUserId = :numericKeyword)
          )
        """)
    Page<PostReport> searchPendingReports(
            @Param("status") PostReportStatus status,
            @Param("keyword") String keyword,
            @Param("numericKeyword") Long numericKeyword,
            Pageable pageable
    );
}
