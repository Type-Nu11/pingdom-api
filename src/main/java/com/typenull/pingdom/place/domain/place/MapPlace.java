package com.typenull.pingdom.place.domain.place;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
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
@Table(
        name = "map_place",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_map_place_kakao_place_id", columnNames = "kakao_place_id")
        }
)
public class MapPlace {

    private static final String TOURIST_INFORMATION_GUARD_ACTIVE = "ACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "map_place_id")
    private Long id;

    @Column(name = "place_name", length = 100, nullable = false)
    private String name;

    @Column(name = "address", length = 255, nullable = false)
    private String address;

    @Column(name = "road_address", length = 255)
    private String roadAddress;

    @Column(name = "jibun_address", length = 255)
    private String jibunAddress;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "geocoding_source", length = 20, nullable = false)
    private GeocodingSource geocodingSource = GeocodingSource.LEGACY;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "operating_status", length = 30, nullable = false)
    private PlaceOperatingStatus operatingStatus = PlaceOperatingStatus.OPERATING;

    @Column(name = "operating_status_checked_at")
    private LocalDateTime operatingStatusCheckedAt;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "english_name", length = 150)
    private String englishName;

    @Column(name = "tourist_summary", length = 500)
    private String touristSummary;

    @Builder.Default
    @ElementCollection
    @CollectionTable(
            name = "map_place_tourist_category",
            joinColumns = @JoinColumn(
                    name = "map_place_id",
                    nullable = false,
                    foreignKey = @ForeignKey(name = "fk_map_place_tourist_category_place")
            )
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "tourist_category", length = 30, nullable = false)
    @Getter(AccessLevel.NONE)
    private Set<TouristCategory> touristCategories = new LinkedHashSet<>();

    @Builder.Default
    @ElementCollection
    @CollectionTable(
            name = "map_place_tourist_guard",
            joinColumns = @JoinColumn(
                    name = "map_place_id",
                    nullable = false,
                    foreignKey = @ForeignKey(name = "fk_map_place_tourist_guard_place")
            )
    )
    @Column(name = "guard_key", length = 16, nullable = false)
    @Getter(AccessLevel.NONE)
    private Set<String> touristInformationGuards = new LinkedHashSet<>();

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "kakao_place_id", length = 50)
    private String kakaoPlaceId;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    // PostGIS geometry (WGS84, lon/lat).
    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "location", columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(name = "user_id")
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

    public long replacePhotoCount(long photoCount) {
        long nextPhotoCount = Math.max(0L, photoCount);
        this.photoCount = nextPhotoCount;
        return nextPhotoCount;
    }

    public Set<TouristCategory> currentTouristCategories() {
        if (touristCategories == null || touristCategories.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(touristCategories));
    }

    boolean hasTouristInformationGuard() {
        return touristInformationGuards != null
                && touristInformationGuards.contains(TOURIST_INFORMATION_GUARD_ACTIVE);
    }

    public void updateTouristInformation(
            String englishName,
            String touristSummary,
            Set<TouristCategory> touristCategories
    ) {
        Set<TouristCategory> nextCategories = touristCategories == null
                ? Set.of()
                : new LinkedHashSet<>(touristCategories);
        this.englishName = englishName;
        this.touristSummary = touristSummary;
        replaceTouristCategories(nextCategories);
        synchronizeTouristInformationGuard(
                englishName != null || touristSummary != null || !nextCategories.isEmpty()
        );
    }

    private void replaceTouristCategories(Set<TouristCategory> nextCategories) {
        if (this.touristCategories == null) {
            this.touristCategories = new LinkedHashSet<>();
        } else {
            try {
                this.touristCategories.clear();
                this.touristCategories.addAll(nextCategories);
                return;
            } catch (UnsupportedOperationException ignored) {
                // Builders may receive an immutable Set; managed Hibernate collections remain mutated in place.
            }
            this.touristCategories = new LinkedHashSet<>();
        }
        this.touristCategories.addAll(nextCategories);
    }

    private void synchronizeTouristInformationGuard(boolean touristInformationPresent) {
        if (this.touristInformationGuards == null) {
            this.touristInformationGuards = new LinkedHashSet<>();
        } else {
            try {
                this.touristInformationGuards.clear();
                if (touristInformationPresent) {
                    this.touristInformationGuards.add(TOURIST_INFORMATION_GUARD_ACTIVE);
                }
                return;
            } catch (UnsupportedOperationException ignored) {
                // Builders may receive an immutable Set; managed Hibernate collections remain mutated in place.
            }
            this.touristInformationGuards = new LinkedHashSet<>();
        }
        if (touristInformationPresent) {
            this.touristInformationGuards.add(TOURIST_INFORMATION_GUARD_ACTIVE);
        }
    }

    public void updateCoordinates(Double latitude, Double longitude, Point location) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.location = location;
    }

    public void updateGeocoding(
            String address,
            String roadAddress,
            String jibunAddress,
            String postalCode,
            Double latitude,
            Double longitude,
            Point location,
            GeocodingSource geocodingSource
    ) {
        this.address = address;
        this.roadAddress = roadAddress;
        this.jibunAddress = jibunAddress;
        this.postalCode = postalCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.location = location;
        this.geocodingSource = geocodingSource;
    }

    public void updateKakaoPlaceId(String kakaoPlaceId) {
        this.kakaoPlaceId = kakaoPlaceId;
    }

    public boolean isOperating() {
        return operatingStatus == PlaceOperatingStatus.OPERATING;
    }

    public void updateOperatingStatus(
            PlaceOperatingStatus operatingStatus,
            LocalDateTime operatingStatusCheckedAt
    ) {
        this.operatingStatus = Objects.requireNonNull(operatingStatus, "operatingStatus must not be null");
        this.operatingStatusCheckedAt = operatingStatusCheckedAt;
    }
}
