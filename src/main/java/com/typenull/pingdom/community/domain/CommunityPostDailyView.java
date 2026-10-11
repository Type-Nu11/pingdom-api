package com.typenull.pingdom.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자·게시글·KST 일자별 최초 커뮤니티 유입만 보관하는 중복 방지 레코드. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "community_post_daily_view", uniqueConstraints = @UniqueConstraint(
        name = "uk_community_post_daily_view",
        columnNames = {"user_id", "community_post_id", "viewed_on"}
))
public class CommunityPostDailyView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_post_daily_view_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "community_post_id", nullable = false)
    private Long communityPostId;

    @Column(name = "viewed_on", nullable = false)
    private LocalDate viewedOn;
}
