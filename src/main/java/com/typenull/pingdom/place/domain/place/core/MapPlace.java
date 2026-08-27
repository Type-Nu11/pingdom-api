package com.typenull.pingdom.place.domain.place.core;

import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingException;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingHour;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingBreakTime;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
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

    @Builder.Default
    @ElementCollection
    @CollectionTable(
            name = "map_place_regular_operating_hour",
            joinColumns = @JoinColumn(
                    name = "map_place_id",
                    nullable = false,
                    foreignKey = @ForeignKey(name = "fk_map_place_regular_operating_hour_place")
            )
    )
    @Getter(AccessLevel.NONE)
    private Set<PlaceRegularOperatingHour> regularOperatingHours = new LinkedHashSet<>();

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "map_place_regular_operating_break_time", joinColumns = @JoinColumn(name = "map_place_id", nullable = false))
    @Getter(AccessLevel.NONE)
    private Set<PlaceRegularOperatingBreakTime> regularOperatingBreakTimes = new LinkedHashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "place", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("exceptionDate ASC")
    @Getter(AccessLevel.NONE)
    private List<PlaceOperatingException> operatingExceptions = new ArrayList<>();

    @Column(name = "category", length = 50)
    private String category;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "discovery_status", length = 20, nullable = false)
    private PlaceDiscoveryStatus discoveryStatus = PlaceDiscoveryStatus.VISIBLE;

    @Column(name = "english_name", length = 150)
    private String englishName;

    @Column(name = "tourist_summary", length = 500)
    private String touristSummary;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "primary_information_source", length = 30, nullable = false)
    private PlaceInformationSourceType primaryInformationSource = PlaceInformationSourceType.LEGACY;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "information_verification_status", length = 30, nullable = false)
    private PlaceInformationVerificationStatus informationVerificationStatus =
            PlaceInformationVerificationStatus.UNVERIFIED;

    @Column(name = "information_verified_at")
    private LocalDateTime informationVerifiedAt;

    @Column(name = "information_verified_by_admin_user_id")
    private Long informationVerifiedByAdminUserId;

    @Column(name = "information_evidence_updated_at")
    private LocalDateTime informationEvidenceUpdatedAt;

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

    @Column(name = "region_code", length = 5)
    private String regionCode;

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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

    public void updateAdministrativeRegion(String regionCode) {
        this.regionCode = regionCode;
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

    public void updateImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isOperating() {
        return operatingStatus == PlaceOperatingStatus.OPERATING;
    }

    public boolean isVisibleInDiscovery() {
        return discoveryStatus == PlaceDiscoveryStatus.VISIBLE;
    }

    public void updateDiscoveryStatus(PlaceDiscoveryStatus discoveryStatus) {
        this.discoveryStatus = Objects.requireNonNull(discoveryStatus, "discoveryStatus must not be null");
    }

    public void updateInformationVerification(
            PlaceInformationSourceType primaryInformationSource,
            PlaceInformationVerificationStatus informationVerificationStatus,
            Long verifiedByAdminUserId,
            LocalDateTime verifiedAt,
            LocalDateTime evidenceUpdatedAt
    ) {
        PlaceInformationVerificationStatus nextStatus = Objects.requireNonNull(
                informationVerificationStatus,
                "informationVerificationStatus must not be null"
        );
        this.primaryInformationSource = Objects.requireNonNull(
                primaryInformationSource,
                "primaryInformationSource must not be null"
        );
        this.informationVerificationStatus = nextStatus;
        this.informationVerifiedByAdminUserId = verifiedByAdminUserId;
        this.informationVerifiedAt = verifiedAt;
        this.informationEvidenceUpdatedAt = evidenceUpdatedAt;
    }

    public Set<PlaceRegularOperatingHour> currentRegularOperatingHours() {
        if (regularOperatingHours == null || regularOperatingHours.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(regularOperatingHours));
    }

    public List<PlaceOperatingException> currentOperatingExceptions() {
        if (operatingExceptions == null || operatingExceptions.isEmpty()) {
            return List.of();
        }
        return List.copyOf(operatingExceptions);
    }

    public void replaceOperatingSchedule(
            Set<PlaceRegularOperatingHour> regularOperatingHours,
            List<PlaceOperatingException> operatingExceptions
    ) {
        replaceOperatingSchedule(regularOperatingHours, Set.of(), operatingExceptions);
    }

    public void replaceOperatingSchedule(
            Set<PlaceRegularOperatingHour> regularOperatingHours,
            Set<PlaceRegularOperatingBreakTime> regularOperatingBreakTimes,
            List<PlaceOperatingException> operatingExceptions
    ) {
        replaceRegularOperatingHours(regularOperatingHours == null ? Set.of() : regularOperatingHours);
        if (this.regularOperatingBreakTimes == null) this.regularOperatingBreakTimes = new LinkedHashSet<>();
        else this.regularOperatingBreakTimes.clear();
        this.regularOperatingBreakTimes.addAll(regularOperatingBreakTimes == null ? Set.of() : regularOperatingBreakTimes);
        replaceOperatingExceptions(operatingExceptions == null ? List.of() : operatingExceptions);
    }

    public void updateOperatingStatus(
            PlaceOperatingStatus operatingStatus,
            LocalDateTime operatingStatusCheckedAt
    ) {
        this.operatingStatus = Objects.requireNonNull(operatingStatus, "operatingStatus must not be null");
        this.operatingStatusCheckedAt = operatingStatusCheckedAt;
    }

    private void replaceRegularOperatingHours(Set<PlaceRegularOperatingHour> nextRegularOperatingHours) {
        if (this.regularOperatingHours == null) {
            this.regularOperatingHours = new LinkedHashSet<>();
        } else {
            try {
                this.regularOperatingHours.clear();
                this.regularOperatingHours.addAll(nextRegularOperatingHours);
                return;
            } catch (UnsupportedOperationException ignored) {
                // Builders may receive an immutable Set; managed Hibernate collections remain mutated in place.
            }
            this.regularOperatingHours = new LinkedHashSet<>();
        }
        this.regularOperatingHours.addAll(nextRegularOperatingHours);
    }

    private void replaceOperatingExceptions(List<PlaceOperatingException> nextOperatingExceptions) {
        if (this.operatingExceptions == null) {
            this.operatingExceptions = new ArrayList<>();
        } else {
            try {
                this.operatingExceptions.clear();
                this.operatingExceptions.addAll(nextOperatingExceptions);
                return;
            } catch (UnsupportedOperationException ignored) {
                // Builders may receive an immutable List; managed Hibernate collections remain mutated in place.
            }
            this.operatingExceptions = new ArrayList<>();
        }
        this.operatingExceptions.addAll(nextOperatingExceptions);
    }
}
