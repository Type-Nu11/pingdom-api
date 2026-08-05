package com.typenull.pingdom.swagger.fixture;

public enum OpenApiCompatibilityDomain {
    APP("app"),
    COMMON("common"),
    WEB("web");

    private final String specName;

    OpenApiCompatibilityDomain(String specName) {
        this.specName = specName;
    }

    public String specName() {
        return specName;
    }
}
