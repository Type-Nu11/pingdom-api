package com.typenull.pingdom.identity.application.query;

import com.typenull.pingdom.identity.domain.User;

public record MyPageQueryResult(
        Long id,
        String username,
        String email,
        Integer birthYear,
        String profileImageUrl,
        String language,
        String country
) {

    public static MyPageQueryResult from(User user) {
        return new MyPageQueryResult(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getBirthYear(),
                user.getProfileImageUrl(),
                user.getLanguage(),
                user.getCountry()
        );
    }
}
