package com.typenull.pingdom.place.domain.place.region;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 외부 해석 결과를 5자리 시·군·구 코드로 저장하는 지역 사전입니다.
 * 갱신은 기존 코드의 이름·갱신 시각만 바꾸며 장소와의 연결은 장소 엔티티가 보관합니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "place_administrative_region")
public class PlaceAdministrativeRegion {

    @Id
    @Column(name = "region_code", length = 5)
    private String code;

    @Column(name = "sido", nullable = false, length = 50)
    private String sido;

    @Column(name = "sigungu", nullable = false, length = 50)
    private String sigungu;

    @Column(name = "region_name", nullable = false, length = 120)
    private String regionName;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static PlaceAdministrativeRegion from(ResolvedPlaceAdministrativeRegion region, LocalDateTime updatedAt) {
        return new PlaceAdministrativeRegion(
                region.code(),
                region.sido(),
                region.sigungu(),
                region.regionName(),
                updatedAt
        );
    }

    public void refresh(ResolvedPlaceAdministrativeRegion region, LocalDateTime updatedAt) {
        this.sido = region.sido();
        this.sigungu = region.sigungu();
        this.regionName = region.regionName();
        this.updatedAt = updatedAt;
    }
}
