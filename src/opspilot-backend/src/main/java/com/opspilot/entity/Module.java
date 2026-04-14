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
 * <p>对应数据库表 {@code t_module}，表示项目下的可独立部署模块。</p>
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

    /** 模块类型: java / node / python */
    private String moduleType;

    /** Git 仓库地址 */
    private String repoUrl;

    /** 分支名称，默认 main */
    private String repoBranch;

    /** 仓库内子路径 */
    private String repoPath;

    /** 构建命令，为空时默认使用 mvn clean package -DskipTests */
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

    /** 逻辑删除标记 */
    @TableLogic
    private Integer deleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 构建工具类型 (maven/npm/gradle等) */
    private String buildTool;

    // ==================== 兼容字段 ====================

    /**
     * 兼容旧代码 setCreatedTime(Date) 调用，自动转换为 LocalDateTime
     */
    public void setCreatedTime(java.util.Date createdTime) {
        if (createdTime != null) {
            this.createTime = createdTime.toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime();
        }
    }

    /**
     * 兼容旧代码 getCreatedTime() 调用，返回 Date 类型
     */
    public java.util.Date getCreatedTime() {
        if (this.createTime != null) {
            return java.util.Date.from(this.createTime.atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
        return null;
    }

    /**
     * 兼容旧代码 setUpdatedTime(Date) 调用，自动转换为 LocalDateTime
     */
    public void setUpdatedTime(java.util.Date updatedTime) {
        if (updatedTime != null) {
            this.updateTime = updatedTime.toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime();
        }
    }

    /**
     * 兼容旧代码 getUpdatedTime() 调用，返回 Date 类型
     */
    public java.util.Date getUpdatedTime() {
        if (this.updateTime != null) {
            return java.util.Date.from(this.updateTime.atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
        return null;
    }
}
