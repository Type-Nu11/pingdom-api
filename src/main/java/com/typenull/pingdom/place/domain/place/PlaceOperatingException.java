package com.typenull.pingdom.place.domain.place;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "map_place_operating_exception",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_map_place_operating_exception_date",
                        columnNames = {"map_place_id", "exception_date"}
                )
        }
)
public class PlaceOperatingException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "operating_exception_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "map_place_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_map_place_operating_exception_place")
    )
    private MapPlace place;

    @Column(name = "exception_date", nullable = false)
    private LocalDate exceptionDate;

    @Column(name = "closed", nullable = false)
    private boolean closed;

    @ElementCollection
    @CollectionTable(
            name = "map_place_operating_exception_hour",
            joinColumns = @JoinColumn(
                    name = "operating_exception_id",
                    nullable = false,
                    foreignKey = @ForeignKey(name = "fk_map_place_operating_exception_hour_exception")
            )
    )
    @Getter(AccessLevel.NONE)
    private Set<PlaceOperatingTimeRange> hours = new LinkedHashSet<>();

    private PlaceOperatingException(
            MapPlace place,
            LocalDate exceptionDate,
            boolean closed,
            Set<PlaceOperatingTimeRange> hours
    ) {
        this.place = place;
        this.exceptionDate = exceptionDate;
        this.closed = closed;
        this.hours = new LinkedHashSet<>(hours);
    }

    public static PlaceOperatingException closed(MapPlace place, LocalDate exceptionDate) {
        return new PlaceOperatingException(place, exceptionDate, true, Set.of());
    }

    public static PlaceOperatingException customHours(
            MapPlace place,
            LocalDate exceptionDate,
            Set<PlaceOperatingTimeRange> hours
    ) {
        return new PlaceOperatingException(place, exceptionDate, false, hours);
    }

    public Set<PlaceOperatingTimeRange> currentHours() {
        if (hours == null || hours.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(hours));
    }
}
