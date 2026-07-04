package com.typenull.pingdom.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.identity.application.query.UserDataExportResult;
import com.typenull.pingdom.identity.application.query.UserDataExportService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.MapBookmark;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class UserDataExportServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MapBookmarkRepository mapBookmarkRepository;

    @Mock
    private MapImageLikeRepository mapImageLikeRepository;

    @InjectMocks
    private UserDataExportService userDataExportService;

    @Test
    void 내_데이터를_정해진_형태로_내보낸다() {
        Long userId = 1L;
        User user = User.builder()
                .id(userId)
                .username("pingdom_user")
                .profileImageUrl("https://cdn.pingdom.com/profiles/user1.png")
                .build();
        MapBookmark bookmark = MapBookmark.builder()
                .id(10L)
                .userId(userId)
                .placeId(123L)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mapBookmarkRepository.findByUserIdOrderByIdAsc(userId)).thenReturn(List.of(bookmark));
        when(mapImageLikeRepository.findRecentMapImageIdsByUserId(eq(userId), any(Pageable.class)))
                .thenReturn(List.of(981L, 812L, 700L));

        UserDataExportResult result = userDataExportService.exportMyData(userId);

        assertThat(result.user().id()).isEqualTo(userId);
        assertThat(result.user().username()).isEqualTo("pingdom_user");
        assertThat(result.user().profileImageUrl()).isEqualTo("https://cdn.pingdom.com/profiles/user1.png");
        assertThat(result.bookmarks())
                .extracting(UserDataExportResult.ExportBookmark::id, UserDataExportResult.ExportBookmark::mapImageId)
                .containsExactly(tuple(10L, 123L));
        assertThat(result.likedMapImageIds()).containsExactly(981L, 812L, 700L);
    }

    @Test
    void 좋아요는_최대_50개만_조회한다() {
        Long userId = 1L;
        User user = User.builder()
                .id(userId)
                .username("pingdom_user")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(mapBookmarkRepository.findByUserIdOrderByIdAsc(userId)).thenReturn(List.of());
        when(mapImageLikeRepository.findRecentMapImageIdsByUserId(eq(userId), any(Pageable.class)))
                .thenReturn(List.of());

        userDataExportService.exportMyData(userId);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(mapImageLikeRepository).findRecentMapImageIdsByUserId(eq(userId), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }
}
