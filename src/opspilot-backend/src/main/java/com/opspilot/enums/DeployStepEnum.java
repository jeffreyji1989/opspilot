package com.opspilot.enums;

/**
 * 发版部署步骤类型枚举
 * 
 * <p>定义发版部署流程中各步骤的类型常量，用于消除硬编码。</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
public enum DeployStepEnum {

    /** 步骤1: 拉取代码 */
    PULL_CODE(1, "拉取代码", "pull_code", 180),

    /** 步骤2: 编译构建 */
    BUILD(2, "编译构建", "build", 300),

    /** 步骤3: 打包产物 */
    PACKAGE(3, "打包产物", "package", 120),

    /** 步骤4: 上传至服务器 */
    UPLOAD(4, "上传至服务器", "upload", 120),

    /** 步骤5: 切换版本 */
    SWITCH_VERSION(5, "切换版本", "switch_version", 30),

    /** 步骤6: 重启服务 */
    RESTART(6, "重启服务", "restart", 60),

    /** 步骤7: 健康检查 */
    HEALTH_CHECK(7, "健康检查", "health_check", 60);

    /** 步骤序号 */
    private final int stepNo;

    /** 步骤名称 */
    private final String stepName;

    /** 步骤标识 */
    private final String stepKey;

    /** 超时时间（秒） */
    private final int timeoutSeconds;

    DeployStepEnum(int stepNo, String stepName, String stepKey, int timeoutSeconds) {
        this.stepNo = stepNo;
        this.stepName = stepName;
        this.stepKey = stepKey;
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getStepNo() {
        return stepNo;
    }

    public String getStepName() {
        return stepName;
    }

    public String getStepKey() {
        return stepKey;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * 根据步骤序号获取枚举
     *
     * @param stepNo 步骤序号
     * @return 对应的枚举值，未匹配则返回 null
     */
    public static DeployStepEnum fromStepNo(int stepNo) {
        for (DeployStepEnum step : values()) {
            if (step.stepNo == stepNo) {
                return step;
            }
        }
        return null;
    }
}
