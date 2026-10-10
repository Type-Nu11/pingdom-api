package com.typenull.pingdom.community.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/** 업로드 전에 등록하는 저장소 key 원장. 미사용 파일도 만료 정리 대상에 남깁니다. */
@Entity @Table(name = "community_post_image") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPostImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long userId;
    private Long postId;
    @Column(name = "s3_key", nullable = false, unique = true, length = 500) private String s3Key;
    @Column(length = 2048) private String imageUrl;
    private Integer position;
    @Column(nullable = false) private LocalDateTime expiresAt;
    public static CommunityPostImage pending(long userId, String key, LocalDateTime expiresAt) {
        CommunityPostImage image = new CommunityPostImage();
        image.userId = userId; image.s3Key = key; image.expiresAt = expiresAt; return image;
    }
    public void uploaded(String url) { this.imageUrl = url; }
    public void attach(long postId, int position) { this.postId = postId; this.position = position; }
}
