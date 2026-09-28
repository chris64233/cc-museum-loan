package com.chris64233.cc.museumloan.domain;

/**
 * 借展申请状态。
 *
 * <p>状态流转：
 * <ul>
 *     <li>{@link #PENDING} 待审批：申请提交后的初始状态；</li>
 *     <li>{@link #APPROVED} 已批准：整组藏品原子批准成功；</li>
 *     <li>{@link #REJECTED} 已拒绝：待审批申请被拒绝，记录拒绝原因；</li>
 *     <li>{@link #CANCELLED} 已取消：已批准且借展尚未开始时取消，记录取消原因并释放全部藏品；</li>
 *     <li>{@link #ACTIVE} 借展中 / {@link #RETURNED} 已归还：由借展开始、归还日期驱动的派生状态，当前版本不提供变更接口，仅用于生命周期完整性。</li>
 * </ul>
 */
public enum LoanStatus {

    PENDING("待审批"),
    APPROVED("已批准"),
    REJECTED("已拒绝"),
    CANCELLED("已取消"),
    ACTIVE("借展中"),
    RETURNED("已归还");

    private final String label;

    LoanStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
