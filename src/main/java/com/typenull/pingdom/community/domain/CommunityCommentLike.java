package com.typenull.pingdom.community.domain;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "community_comment_like") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityCommentLike {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long commentId;
    @Column(nullable = false) private Long userId;
}
