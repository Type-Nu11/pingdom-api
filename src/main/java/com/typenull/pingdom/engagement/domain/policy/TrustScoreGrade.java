package com.typenull.pingdom.engagement.domain.policy;

public enum TrustScoreGrade {
    HIGH,
    NORMAL,
    LOW;

    public static TrustScoreGrade fromScore(int trustScore) {
        if (trustScore >= 80) {
            return HIGH;
        }
        if (trustScore >= 50) {
            return NORMAL;
        }
        return LOW;
    }
}
