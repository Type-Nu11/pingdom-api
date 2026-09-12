package com.typenull.pingdom.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CommunityPostPlaceTest {

    @Test
    void 게시글과_장소를_ID_참조로_연결한다() {
        CommunityPost communityPost = Mockito.mock(CommunityPost.class);
        MapPlace mapPlace = Mockito.mock(MapPlace.class);

        CommunityPostPlace link = CommunityPostPlace.connect(communityPost, mapPlace);

        assertThat(link.getCommunityPost()).isSameAs(communityPost);
        assertThat(link.getMapPlace()).isSameAs(mapPlace);
    }

    @Test
    void 게시글_또는_장소_없이_연결할_수_없다() {
        MapPlace mapPlace = Mockito.mock(MapPlace.class);

        assertThatNullPointerException()
                .isThrownBy(() -> CommunityPostPlace.connect(null, mapPlace));
        assertThatNullPointerException()
                .isThrownBy(() -> CommunityPostPlace.connect(Mockito.mock(CommunityPost.class), null));
    }
}
