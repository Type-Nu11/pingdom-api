package com.typenull.pingdom.integration.swagger.fixture;

public enum OpenApiCompatibilityDomain {
    APP("app"),
    COMMON("common"),
    CONSULTING("consulting"),
    WEB("openapi");

    private final String specName;

    OpenApiCompatibilityDomain(String specName) {
        this.specName = specName;
    }

    public String specName() {
        return specName;
    }
}
