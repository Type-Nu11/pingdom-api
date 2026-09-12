package com.typenull.pingdom.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.community.api.dto.CommunityPostListResponse;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class CommunityPostQueryServiceTest {

    private final CommunityPostRepository communityPostRepository = mock(CommunityPostRepository.class);
    private final CommunityPostQueryService service = new CommunityPostQueryService(communityPostRepository);

    @Test
    void 카테고리에_속한_게시글의_ID와_제목만_페이지로_조회한다() {
        List<CommunityPostListResponse.Item> items = List.of(
                new CommunityPostListResponse.Item(2L, "두 번째 게시글"),
                new CommunityPostListResponse.Item(1L, "첫 번째 게시글")
        );
        when(communityPostRepository.findListItemsByCategoryId(any(), any()))
                .thenReturn(new PageImpl<>(items, PageRequest.of(0, 20), 22));

        CommunityPostListResponse response = service.findByCategory("TRAVEL", 1, 20);

        assertThat(response.posts()).containsExactlyElementsOf(items);
        assertThat(response.totalCount()).isEqualTo(22);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isTrue();
        verify(communityPostRepository).findListItemsByCategoryId(any(), any());
    }

    @Test
    void 지원하지_않는_카테고리는_조회하지_않는다() {
        assertThatThrownBy(() -> service.findByCategory("UNKNOWN", 1, 20))
                .isInstanceOf(CommunityException.class)
                .hasMessage("사용할 수 없는 게시글 카테고리입니다.");

        verify(communityPostRepository, never()).findListItemsByCategoryId(any(), any());
    }
}
