package com.typenull.pingdom.place.domain.conversion;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 길찾기·외부 지도 이동의 사용자 행동과 재요청 중복 키를 저장.
 * provider와 중복 키는 공백을 제거하며 생성 시각은 전달받은 발생 시각과 동일하게 기록.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "map_link_conversion_event", uniqueConstraints = @UniqueConstraint(
        name = "uq_map_link_conversion_deduplication", columnNames = "deduplication_key"))
public class MapLinkConversionEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "map_link_conversion_event_id")
    private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "map_place_id", nullable = false) private Long placeId;
    @Enumerated(EnumType.STRING) @Column(name = "link_type", nullable = false, length = 30)
    private MapLinkConversionType linkType;
    @Column(name = "provider", nullable = false, length = 30) private String provider;
    @Column(name = "deduplication_key", nullable = false, unique = true, length = 200)
    private String deduplicationKey;
    @Column(name = "occurred_at", nullable = false) private LocalDateTime occurredAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    private MapLinkConversionEvent(Long userId, Long placeId, MapLinkConversionType linkType,
                                   String provider, String deduplicationKey, LocalDateTime occurredAt) {
        if (userId == null || userId <= 0 || placeId == null || placeId <= 0 || linkType == null
                || provider == null || provider.isBlank() || deduplicationKey == null || deduplicationKey.isBlank()
                || occurredAt == null) throw new IllegalArgumentException("invalid map link conversion event");
        this.userId = userId; this.placeId = placeId; this.linkType = linkType;
        this.provider = provider.trim(); this.deduplicationKey = deduplicationKey.trim();
        this.occurredAt = occurredAt; this.createdAt = occurredAt;
    }

    public static MapLinkConversionEvent create(Long userId, Long placeId, MapLinkConversionType linkType,
                                                String provider, String deduplicationKey, LocalDateTime occurredAt) {
        return new MapLinkConversionEvent(userId, placeId, linkType, provider, deduplicationKey, occurredAt);
    }
}
