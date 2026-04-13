package com.opspilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@TableName("t_deploy_record")
public class DeployRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long moduleId;
    private Long instanceId;
    private String deployNo;
    private String version;
    private String gitBranch;
    private String gitCommit;
    private String deployType;
    private Long scheduleId;
    private Long operatorId;
    private Integer status;
    private Long rollbackFromId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationSeconds;
    private String errorMessage;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ==================== 兼容字段 ====================

    /**
     * 兼容字段：代码中使用 setOperator 直接设置 operatorId
     */
    public void setOperator(String operator) {
        // operator 作为显示名存储，operatorId 为数字ID
        // 如果 operator 是数字字符串，转为 Long
        try {
            this.operatorId = Long.parseLong(operator);
        } catch (NumberFormatException e) {
            // 非数字则忽略
        }
    }

    public String getOperator() {
        return this.operatorId != null ? this.operatorId.toString() : null;
    }

    /**
     * 兼容字段：映射到 createTime
     */
    public void setCreatedTime(Date createdTime) {
        if (createdTime != null) {
            this.createTime = createdTime.toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime();
        }
    }

    public Date getCreatedTime() {
        if (this.createTime != null) {
            return Date.from(this.createTime.atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
        return null;
    }
}
