package com.typenull.pingdom.post.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import com.typenull.pingdom.place.domain.place.MapPlace;

import java.time.LocalDateTime;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
//@Table(
//        uniqueConstraints = {
//                @UniqueConstraint(
//                        name = "uk_map_image_user_place",
//                        columnNames = {"user_id", "map_place_id"}
//                )
//        }
//)
public class MapImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "map_image_id")
    private Long id;

    @Column(name = "image_url", length = 500, nullable = false)
    private String imageUrl;

    @Column(name = "s3_key", length = 500, nullable = false)
    private String s3Key;

    @Column(name = "title", length = 100, nullable = false)
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
    private long likeCount = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_place_id")
    private MapPlace mapPlace;

    public void update(String title, String description, String imageUrl, String s3Key) {
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
        this.s3Key = s3Key;
    }

    public void reassignPlace(MapPlace targetPlace) {
        this.mapPlace = targetPlace;
    }
}
