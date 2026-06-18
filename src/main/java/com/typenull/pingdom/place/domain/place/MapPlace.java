package com.typenull.pingdom.place.domain.place;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
//더미 데이터 떄문에 임시로 끄기, 정식으로 출시 할때 키기
//@Table(
//        name = "map_place",
//        uniqueConstraints = {
//                @UniqueConstraint(name = "uk_map_place_kakao_place_id", columnNames = "kakao_place_id")
//        }
//)
public class MapPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "map_place_id")
    private Long id;

    @Column(name = "place_name", length = 100, nullable = false)
    private String name;

    @Column(name = "address", length = 255, nullable = false)
    private String address;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "kakao_place_id", length = 50)
    private String kakaoPlaceId;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    // PostGIS geometry (WGS84, lon/lat). Nullable for backward compatibility with existing rows.
    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "location", columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "registrant", nullable = false)
    private String registrant;

    // Store the aggregate once so place level reads do not trigger post count queries.
    @Builder.Default
    @ColumnDefault("0")
    @Column(name = "photo_count", nullable = false)
    private Long photoCount = 0L;

    public long currentPhotoCount() {
        return photoCount == null ? 0L : photoCount;
    }

    public long increasePhotoCount() {
        long nextPhotoCount = currentPhotoCount() + 1;
        this.photoCount = nextPhotoCount;
        return nextPhotoCount;
    }

    public long decreasePhotoCount() {
        long nextPhotoCount = Math.max(0L, currentPhotoCount() - 1);
        this.photoCount = nextPhotoCount;
        return nextPhotoCount;
    }
}
