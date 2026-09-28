package com.chris64233.cc.museumloan.domain;

/**
 * 风险等级，rank 越大风险越高。
 * 机构可承担的最高风险等级（以及藏品/运输方案的风险等级）均使用本枚举，
 * “风险能力足够” 即机构最高等级的 rank 不小于被比较的风险等级。
 */
public enum RiskLevel {

    LOW(1, "低"),
    MEDIUM(2, "中"),
    HIGH(3, "高");

    private final int rank;
    private final String label;

    RiskLevel(int rank, String label) {
        this.rank = rank;
        this.label = label;
    }

    public int getRank() {
        return rank;
    }

    public String getLabel() {
        return label;
    }

    /** 当前等级是否足以承担 other 所代表的风险。 */
    public boolean covers(RiskLevel other) {
        return this.rank >= other.rank;
    }
}
