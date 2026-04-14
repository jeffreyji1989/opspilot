package com.opspilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模块实体
 *
 * <p>对应数据库表 {@code t_module}，表示项目下的可独立部署模块。
 * 支持 7 种类型：JAR/WAR/Vue/React/Node.js/Android/Flutter。</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@Data
@TableName("t_module")
public class Module {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String moduleName;

    /** 模块类型: JAR/WAR/Vue/React/Node.js/Android/Flutter */
    private String moduleType;

    /** Git 仓库地址 */
    private String repoUrl;

    /** 分支名称，默认 main */
    private String repoBranch;

    /** 仓库内子路径（Monorepo 场景使用） */
    private String repoPath;

    /** Maven 模块名称（用于多模块项目的 -pl 参数） */
    private String mavenModuleName;

    /** JDK 版本：8/11/17/21 */
    private String jdkVersion;

    /** Node.js 版本：14/16/18/20 */
    private String nodeVersion;

    /** 构建命令 */
    private String buildCommand;

    /** 构建产物路径 */
    private String artifactPath;

    /** 部署路径 */
    private String deployPath;

    /** 健康检查路径 */
    private String healthCheckPath;

    /** 部署顺序 */
    private Integer deployOrder;

    /** 模块描述 */
    private String description;

    /** 关联的 Git 认证 ID */
    private Long gitCredId;

    /** 构建工具类型 (maven/npm/gradle等) */
    private String buildTool;

    /** 模块类型枚举数字（兼容旧字段）: 0=独立仓库，1=Monorepo子模块，2=Maven多模块 */
    private Integer type;

    /** 逻辑删除标记 */
    @TableLogic
    private Integer deleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    // ==================== 兼容字段 ====================

    public void setCreatedTime(java.util.Date createdTime) {
        if (createdTime != null) {
            this.createTime = createdTime.toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime();
        }
    }

    public java.util.Date getCreatedTime() {
        if (this.createTime != null) {
            return java.util.Date.from(this.createTime.atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
        return null;
    }

    public void setUpdatedTime(java.util.Date updatedTime) {
        if (updatedTime != null) {
            this.updateTime = updatedTime.toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime();
        }
    }

    public java.util.Date getUpdatedTime() {
        if (this.updateTime != null) {
            return java.util.Date.from(this.updateTime.atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
        return null;
    }
}
