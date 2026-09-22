package com.typenull.pingdom.place.domain.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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

/**
 * 리뷰 작성 전 업로드한 사진의 소유 회원·장소·만료와 연결 결과를 보관합니다.
 * connect는 미연결 상태만 확인하므로 소유권·만료·사진 개수 검사는 서비스에서 선행해야 합니다.
 */
@Getter
@Entity
@Table(name = "place_review_media_upload")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceReviewMediaUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_review_media_upload_id")
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "s3_key", nullable = false, unique = true, length = 500)
    private String s3Key;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaceReviewMediaUploadStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id")
    private PlaceReview review;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private PlaceReviewMediaUpload(
            Long placeId,
            Long userId,
            String s3Key,
            String imageUrl,
            String contentType,
            long fileSize,
            LocalDateTime expiresAt,
            LocalDateTime createdAt
    ) {
        this.placeId = Objects.requireNonNull(placeId);
        this.userId = Objects.requireNonNull(userId);
        this.s3Key = Objects.requireNonNull(s3Key);
        this.imageUrl = Objects.requireNonNull(imageUrl);
        this.contentType = Objects.requireNonNull(contentType);
        this.fileSize = fileSize;
        this.status = PlaceReviewMediaUploadStatus.UPLOADED;
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static PlaceReviewMediaUpload upload(
            Long placeId,
            Long userId,
            String s3Key,
            String imageUrl,
            String contentType,
            long fileSize,
            LocalDateTime expiresAt,
            LocalDateTime createdAt
    ) {
        return new PlaceReviewMediaUpload(
                placeId, userId, s3Key, imageUrl, contentType, fileSize, expiresAt, createdAt
        );
    }

    public boolean isOwnedBy(Long userId, Long placeId) {
        return this.userId.equals(userId) && this.placeId.equals(placeId);
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public void connect(PlaceReview review, int displayOrder, LocalDateTime now) {
        if (status != PlaceReviewMediaUploadStatus.UPLOADED) {
            throw new IllegalStateException("이미 연결된 리뷰 사진입니다.");
        }
        this.review = Objects.requireNonNull(review);
        this.displayOrder = displayOrder;
        this.status = PlaceReviewMediaUploadStatus.CONNECTED;
        this.connectedAt = Objects.requireNonNull(now);
    }
}
