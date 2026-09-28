package com.chris64233.cc.museumloan.domain;

/**
 * 风险等级，按等级值升序。机构可承担最高等级 &gt;= 方案等级时才允许借出。
 */
public enum RiskLevel {
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    private final int level;

    RiskLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public boolean covers(RiskLevel other) {
        return this.level >= other.level;
    }
}
