package com.typenull.pingdom.identity.application.query;

import com.typenull.pingdom.identity.domain.User;
import java.util.List;

public record UserDataExportResult(
        ExportUser user,
        List<ExportBookmark> bookmarks,
        List<Long> likedMapImageIds
) {

    public static UserDataExportResult of(
            User user,
            List<ExportBookmark> bookmarks,
            List<Long> likedMapImageIds
    ) {
        return new UserDataExportResult(
                new ExportUser(user.getId(), user.getUsername(), user.getProfileImageUrl()),
                bookmarks,
                likedMapImageIds
        );
    }

    public record ExportUser(
            Long id,
            String username,
            String profileImageUrl
    ) {
    }

    public record ExportBookmark(
            Long id,
            Long placeId
    ) {
    }
}
