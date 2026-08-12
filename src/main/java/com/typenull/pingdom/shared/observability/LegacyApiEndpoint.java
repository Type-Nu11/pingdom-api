package com.typenull.pingdom.shared.observability;

public enum LegacyApiEndpoint {

    PLACE_LIST("GET", "/place"),
    PLACE_DETAIL("GET", "/place/{id}"),
    PLACE_COORDINATE_CREATE("POST", "/map/places/coordinates"),
    PLACE_UPLOAD("POST", "/map/places/upload"),
    PLACE_DELETE("DELETE", "/map/places/{id}/delete"),
    BOOKMARK_LIST("GET", "/users/bookmarks"),
    BOOKMARK_CREATE("POST", "/map/bookmarks"),
    BOOKMARK_DELETE("DELETE", "/map/bookmarks"),
    POST_CREATE("POST", "/map/post/create"),
    POST_UPDATE("POST", "/map/post/{id}/update"),
    POST_DELETE("DELETE", "/map/post/{id}/delete"),
    POST_REPORT("POST", "/map/post/{id}/report"),
    FCM_TOKEN_UPDATE("PATCH", "/firebase/fcm-token");

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
