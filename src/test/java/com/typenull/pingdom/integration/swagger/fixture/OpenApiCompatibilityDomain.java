package com.typenull.pingdom.integration.swagger.fixture;

/**
 * 호환성 fixture의 문서 그룹을 baseline 파일 이름에 연결한다. WEB은 통합 openapi.json을 사용한다.
 */
public enum OpenApiCompatibilityDomain {
    APP("app"),
    COMMON("common"),
    CONSULTING("consulting"),
    WEB("openapi");

    private final String specName;

    /**
     * 문서 그룹이 읽을 baseline 파일 이름을 연결한다.
     */
    OpenApiCompatibilityDomain(String specName) {
        this.specName = specName;
    }

    /**
     * baseline JSON 파일명에 쓰는 그룹 식별자를 반환한다. WEB은 openapi.json을 가리킨다.
     */
    public String specName() {
        return specName;
    }
}
