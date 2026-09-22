package com.typenull.pingdom.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CommunityPostPlaceTest {

    /**
     * 게시글과 장소를 연결하면 전달한 두 객체 참조를 그대로 보존하는지 검증한다.
     */
    @Test
    void connectsPostAndPlaceReferences() {
        CommunityPost communityPost = Mockito.mock(CommunityPost.class);
        MapPlace mapPlace = Mockito.mock(MapPlace.class);

        CommunityPostPlace link = CommunityPostPlace.connect(communityPost, mapPlace);

        assertThat(link.getCommunityPost()).isSameAs(communityPost);
        assertThat(link.getMapPlace()).isSameAs(mapPlace);
    }

    /**
     * 게시글 또는 장소가 null이면 연결 생성 시 NullPointerException을 반환하는지 검증한다.
     */
    @Test
    void rejectsMissingLinkEndpoints() {
        MapPlace mapPlace = Mockito.mock(MapPlace.class);

        assertThatNullPointerException()
                .isThrownBy(() -> CommunityPostPlace.connect(null, mapPlace));
        assertThatNullPointerException()
                .isThrownBy(() -> CommunityPostPlace.connect(Mockito.mock(CommunityPost.class), null));
    }
}
