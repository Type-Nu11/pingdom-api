package com.typenull.pingdom.moderation.domain.ad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admin_ad")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAd {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_ad_id")
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "redirect_url", nullable = false, length = 500)
    private String redirectUrl;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private AdminAd(
            String title,
            String imageUrl,
            String redirectUrl,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime createdAt
    ) {
        this.title = title;
        this.imageUrl = imageUrl;
        this.redirectUrl = redirectUrl;
        this.startAt = startAt;
        this.endAt = endAt;
        this.createdAt = createdAt;
    }
}
