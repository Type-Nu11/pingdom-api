package com.typenull.pingdom.place.domain.place.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 회원과 장소의 현재 북마크 연결입니다. 회원·장소 쌍의 DB 고유 제약으로 중복 저장을 제한합니다.
 * 장소 병합 시 연결 ID만 재지정하며 변경 이력 기록은 호출 서비스의 책임입니다.
 */
@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(
        name = "map_bookmark",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_map_bookmark_user_place", columnNames = {"user_id", "place_id"})
        }
)
public class MapBookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void reassignPlace(Long targetPlaceId) {
        this.placeId = targetPlaceId;
    }
}
