package com.opspilot.enums;

/**
 * 发版部署状态枚举
 * 
 * <p>定义发版任务在整个生命周期中的状态流转。
 * 状态机：待执行(0) → 执行中(1) → 成功(2) / 失败(3) → 回退中(5) → 已回退(6)</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
public enum DeployStatusEnum {

    /** 待执行：任务已创建，等待开始 */
    PENDING(0, "待执行"),

    /** 执行中：正在执行发版步骤 */
    RUNNING(1, "执行中"),

    /** 成功：所有步骤执行完毕 */
    SUCCESS(2, "成功"),

    /** 失败：某一步骤执行异常 */
    FAILED(3, "失败"),

    /** 回退中：正在执行版本回退 */
    ROLLING_BACK(5, "回退中"),

    /** 已回退：版本已回退到上一稳定版本 */
    ROLLED_BACK(6, "已回退");

    /** 状态码 */
    private final int code;

    /** 状态描述 */
    private final String description;

    DeployStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据状态码获取枚举
     *
     * @param code 状态码
     * @return 对应的枚举值，未匹配则返回 null
     */
    public static DeployStatusEnum fromCode(int code) {
        for (DeployStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
