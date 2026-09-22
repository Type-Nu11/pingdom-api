package com.typenull.pingdom.place.application.service.recommendation.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PlaceRecommendationUserSignalLoaderTest {
    /**
     * 북마크 이력이 200개여도 개인화 시드는 최대 64개로 제한되는지 확인.
     */
    @Test
    void capsPersonalSignalSeeds() {
        var bookmarks = mock(MapBookmarkRepository.class);
        var likes = mock(MapImageLikeRepository.class);
        var uploads = mock(MapImageRepository.class);
        when(bookmarks.findPlaceIdsByUserId(7L)).thenReturn(IntStream.rangeClosed(1, 200).boxed().map(Long::valueOf).toList());
        when(likes.findPlaceIdsByUserId(7L)).thenReturn(java.util.List.of());
        when(uploads.findPlaceIdsByUserId(7L)).thenReturn(java.util.List.of());

        var context = new PlaceRecommendationUserSignalLoader(bookmarks, likes, uploads).loadUserSignals(7L);

        assertThat(context.seedWeights()).hasSize(64);
    }
}
