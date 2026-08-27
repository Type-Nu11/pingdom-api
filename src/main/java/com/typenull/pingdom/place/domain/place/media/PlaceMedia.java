package com.typenull.pingdom.place.domain.place.media;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.util.StringUtils;

@Entity
@Getter
@Table(name = "place_media")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_media_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "map_place_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_place_media_place")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MapPlace place;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    private PlaceMediaPurpose purpose;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "s3_key", length = 500)
    private String s3Key;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "thumbnail_s3_key", length = 500)
    private String thumbnailS3Key;

    @Column(name = "source_map_image_id")
    private Long sourceMapImageId;

    /** 신청 대표 이미지에서 승격됐음을 기록해 승인 재시도와 backfill을 멱등 처리합니다. */
    @Column(name = "source_registration_attachment_id")
    private Long sourceRegistrationAttachmentId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private PlaceMedia(
            MapPlace place,
            PlaceMediaPurpose purpose,
            String imageUrl,
            String s3Key,
            String thumbnailUrl,
            String thumbnailS3Key,
            Long sourceMapImageId,
            Long sourceRegistrationAttachmentId,
            int displayOrder,
            LocalDateTime createdAt
    ) {
        this.place = Objects.requireNonNull(place, "place must not be null");
        this.purpose = Objects.requireNonNull(purpose, "purpose must not be null");
        this.imageUrl = requireText(imageUrl, "imageUrl must not be blank");
        this.s3Key = trimToNull(s3Key);
        this.thumbnailUrl = trimToNull(thumbnailUrl);
        this.thumbnailS3Key = trimToNull(thumbnailS3Key);
        validateSourceMapImage(purpose, sourceMapImageId);
        if (purpose != PlaceMediaPurpose.EXPLORATION && sourceRegistrationAttachmentId != null) {
            throw new IllegalArgumentException("registration attachment source는 exploration media에만 연결할 수 있습니다.");
        }
        this.sourceMapImageId = sourceMapImageId;
        this.sourceRegistrationAttachmentId = sourceRegistrationAttachmentId;
        this.displayOrder = Math.max(0, displayOrder);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = createdAt;
    }

    public static PlaceMedia verification(
            MapPlace place,
            String imageUrl,
            String s3Key,
            String thumbnailUrl,
            String thumbnailS3Key,
            Long sourceMapImageId,
            LocalDateTime createdAt
    ) {
        return new PlaceMedia(
                place,
                PlaceMediaPurpose.VERIFICATION,
                imageUrl,
                s3Key,
                thumbnailUrl,
                thumbnailS3Key,
                sourceMapImageId,
                null,
                0,
                createdAt
        );
    }

    public static PlaceMedia exploration(
            MapPlace place,
            String imageUrl,
            String s3Key,
            String thumbnailUrl,
            String thumbnailS3Key,
            int displayOrder,
            LocalDateTime createdAt
    ) {
        return new PlaceMedia(
                place,
                PlaceMediaPurpose.EXPLORATION,
                imageUrl,
                s3Key,
                thumbnailUrl,
                thumbnailS3Key,
                null,
                null,
                displayOrder,
                createdAt
        );
    }

    public static PlaceMedia explorationFromRegistrationAttachment(
            MapPlace place,
            String imageUrl,
            String s3Key,
            Long sourceRegistrationAttachmentId,
            int displayOrder,
            LocalDateTime createdAt
    ) {
        if (sourceRegistrationAttachmentId == null) {
            throw new IllegalArgumentException("sourceRegistrationAttachmentId must not be null");
        }
        return new PlaceMedia(
                place,
                PlaceMediaPurpose.EXPLORATION,
                imageUrl,
                s3Key,
                null,
                null,
                null,
                sourceRegistrationAttachmentId,
                displayOrder,
                createdAt
        );
    }

    public void updateDisplayOrder(int displayOrder) {
        this.displayOrder = Math.max(0, displayOrder);
        this.updatedAt = LocalDateTime.now();
    }

    private void validateSourceMapImage(PlaceMediaPurpose purpose, Long sourceMapImageId) {
        if (purpose == PlaceMediaPurpose.VERIFICATION && sourceMapImageId == null) {
            throw new IllegalArgumentException("sourceMapImageId must not be null for verification media");
        }
        if (purpose == PlaceMediaPurpose.EXPLORATION && sourceMapImageId != null) {
            throw new IllegalArgumentException("sourceMapImageId must be null for exploration media");
        }
    }

    private String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
