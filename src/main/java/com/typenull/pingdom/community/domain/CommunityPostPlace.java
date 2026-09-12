package com.typenull.pingdom.community.domain;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 커뮤니티 게시글과 장소의 다중 연결을 나타낸다.
 *
 * <p>장소 이름을 저장하지 않고 장소 식별자만 참조한다. 동일 게시글에 같은 장소를 두 번 연결할 수 없다.</p>
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "community_post_place",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_community_post_place_post_place",
                columnNames = {"community_post_id", "map_place_id"}
        )
)
public class CommunityPostPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_post_place_id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "community_post_id", nullable = false)
    private CommunityPost communityPost;

    @ManyToOne(optional = false)
    @JoinColumn(name = "map_place_id", nullable = false)
    private MapPlace mapPlace;

    private CommunityPostPlace(CommunityPost communityPost, MapPlace mapPlace) {
        this.communityPost = Objects.requireNonNull(communityPost, "communityPost must not be null");
        this.mapPlace = Objects.requireNonNull(mapPlace, "mapPlace must not be null");
    }

    public static CommunityPostPlace connect(CommunityPost communityPost, MapPlace mapPlace) {
        return new CommunityPostPlace(communityPost, mapPlace);
    }
}
