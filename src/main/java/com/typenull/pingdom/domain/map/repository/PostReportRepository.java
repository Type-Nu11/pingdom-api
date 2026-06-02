package com.typenull.pingdom.domain.map.repository;

import com.typenull.pingdom.domain.map.domain.PostReport;
import java.util.Collection;
import java.util.List;
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PostReport pr set pr.mapImage = null where pr.mapImage.id = :mapImageId")
    int detachMapImageByMapImageId(@Param("mapImageId") Long mapImageId);
}
