package com.typenull.pingdom.shared.observability;

public enum LegacyApiEndpoint {

    PLACE_COORDINATE_CREATE_PUBLIC("POST", "/places/coordinates"),
    PLACE_UPLOAD_PUBLIC("POST", "/places/upload"),
    POST_COORDINATE_PLACE_CREATE("POST", "/map/posts (coordinate place creation)");

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
