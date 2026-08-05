package com.typenull.pingdom.place.domain.place.information;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 장소별 검증 근거 집계와 가장 최근 검증 근거를 조회하기 위한 요약 모델. */
@Entity
@Getter
@Table(name = "place_information_verification_summary")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceInformationVerificationSummary {

    @Id
    @Column(name = "map_place_id")
    private Long placeId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "map_place_id")
    private MapPlace place;

    @Column(name = "verified_evidence_count", nullable = false)
    private int verifiedEvidenceCount;

    @Column(name = "last_verified_evidence_id")
    private Long lastVerifiedEvidenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_verified_source_type", length = 30)
    private PlaceInformationSourceType lastVerifiedSourceType;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
