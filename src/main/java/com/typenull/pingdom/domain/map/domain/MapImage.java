package com.typenull.pingdom.domain.map.domain;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MapImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "map_image_id")
    private Long id;

    @Column(name = "image_url", length = 500, nullable = false)
    private String imageUrl;

    @Column(name = "s3_key", length = 500, nullable = false)
    private String s3Key;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username")
    private String username;

    @Column(name = "created_time")
    @CreationTimestamp // save시 자동으로 현재시간 저장
    private LocalDateTime createdAt;

    @Column(nullable = false, name = "like_count")
    private Long likeCount = 0L;

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        this.likeCount--;
    }

    @ManyToOne
    @JoinColumn(name = "map_place_id")
    private MapPlace mapPlace;
}
