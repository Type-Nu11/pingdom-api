package com.typenull.pingdom.analysis.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 프론트가 전달하는 입지 분석 조건이다. 지역은 필수이며 나머지 조건은 AI 입력으로 사용한다. */
public class LocationAnalysisRequest {

    @NotBlank(message = "지역은 필수입니다.")
    private String region;

    @NotBlank(message = "가게 업종은 필수입니다.")
    private String category;
    private String targetCustomerGroup;
    private String operatingHours;

    @JsonIgnore
    private final Map<String, Object> additionalCriteria = new LinkedHashMap<>();

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTargetCustomerGroup() {
        return targetCustomerGroup;
    }

    public void setTargetCustomerGroup(String targetCustomerGroup) {
        this.targetCustomerGroup = targetCustomerGroup;
    }

    public String getOperatingHours() {
        return operatingHours;
    }

    public void setOperatingHours(String operatingHours) {
        this.operatingHours = operatingHours;
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
        criteria.put("category", category);
        criteria.put("targetCustomerGroup", targetCustomerGroup);
        criteria.put("operatingHours", operatingHours);
        return criteria;
    }
}
