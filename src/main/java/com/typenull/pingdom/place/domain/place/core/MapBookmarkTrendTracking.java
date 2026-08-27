package com.typenull.pingdom.place.domain.place.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;

/** 이력 집계가 신뢰 가능한 기준 시점을 단일 행으로 관리합니다. */
@Entity
@Getter
@Table(name = "map_bookmark_trend_tracking")
public class MapBookmarkTrendTracking {

    @Id
    private Boolean id;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;
}
