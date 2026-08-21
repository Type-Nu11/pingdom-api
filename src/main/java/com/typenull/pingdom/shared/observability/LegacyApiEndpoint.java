package com.typenull.pingdom.shared.observability;

public enum LegacyApiEndpoint {

    PLACE_COORDINATE_CREATE_PUBLIC("POST", "/places/coordinates"),
    PLACE_UPLOAD_PUBLIC("POST", "/places/upload"),
    POST_COORDINATE_PLACE_CREATE("POST", "/map/posts (coordinate place creation)"),
    PLACE_RECOMMENDATIONS_GET("GET", "/place/recommendations"),
    PLACE_RECOMMENDATIONS_CLICK("POST", "/place/recommendations/click"),
    PLACE_RECOMMENDATION_EXPLANATION_GET("GET", "/place/recommendations/{requestId}/explanation"),
    MAP_LIKED_POSTS_GET("GET", "/map/like"),
    OAUTH_GOOGLE_GET("GET", "/auth/google"),
    ADMIN_AD_LIST_GET("GET", "/admin/ad"),
    ADMIN_NOTIFICATIONS_USER_ID_GET("GET", "/admin/notifications?userId"),
    ADMIN_BANNED_USERS_BANNED_FROM_GET("GET", "/admin/users/banned?bannedFrom"),
    ADMIN_BANNED_USERS_BANNED_TO_GET("GET", "/admin/users/banned?bannedTo");

    private final String method;
    private final String path;

    LegacyApiEndpoint(String method, String path) {
        this.method = method;
        this.path = path;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }
}
