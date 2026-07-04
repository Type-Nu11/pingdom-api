package com.typenull.pingdom.identity.application.query;

import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDataExportService {

    private static final int LIKE_EXPORT_LIMIT = 50;

    private final UserRepository userRepository;
    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapImageLikeRepository mapImageLikeRepository;

    @Transactional(readOnly = true)
    public UserDataExportResult exportMyData(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsersException(UsersErrorCode.USER_NOT_FOUND));

        List<UserDataExportResult.ExportBookmark> bookmarks = mapBookmarkRepository.findByUserIdOrderByIdAsc(userId)
                .stream()
                .map(bookmark -> new UserDataExportResult.ExportBookmark(
                        bookmark.getId(),
                        bookmark.getPlaceId()
                ))
                .toList();

        List<Long> likedMapImageIds = mapImageLikeRepository.findRecentMapImageIdsByUserId(
                userId,
                PageRequest.of(0, LIKE_EXPORT_LIMIT)
        );

        return UserDataExportResult.of(user, bookmarks, likedMapImageIds);
    }
}
