package com.typenull.pingdom.analysis.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 프론트가 전달하는 입지 분석 조건이다. 지역만 필수이고 나머지는 확장 가능하다. */
public class LocationAnalysisRequest {

    @NotBlank(message = "지역은 필수입니다.")
    private String region;

    private String desiredIndustry;
    private String targetAge;
    private String targetGender;

    @JsonIgnore
    private final Map<String, Object> additionalCriteria = new LinkedHashMap<>();

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getDesiredIndustry() {
        return desiredIndustry;
    }

    public void setDesiredIndustry(String desiredIndustry) {
        this.desiredIndustry = desiredIndustry;
    }

    public String getTargetAge() {
        return targetAge;
    }

    public void setTargetAge(String targetAge) {
        this.targetAge = targetAge;
    }

    public String getTargetGender() {
        return targetGender;
    }

    public void setTargetGender(String targetGender) {
        this.targetGender = targetGender;
    }

    @JsonAnySetter
    public void addAdditionalCriterion(String name, Object value) {
        additionalCriteria.put(name, value);
    }

    public Map<String, Object> additionalCriteria() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(additionalCriteria));
    }

    public Map<String, Object> toCriteriaMap() {
        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("region", region);
        criteria.put("desiredIndustry", desiredIndustry);
        criteria.put("targetAge", targetAge);
        criteria.put("targetGender", targetGender);
        criteria.putAll(additionalCriteria);
        return criteria;
    }
}
