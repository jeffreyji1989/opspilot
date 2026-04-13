package com.opspilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@TableName("t_deploy_step")
public class DeployStep {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deployRecordId;
    private String stepName;
    private Integer stepOrder;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationSeconds;
    private String logOutput;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ==================== 兼容字段（代码中使用简写名称） ====================

    /**
     * 兼容字段：映射到 deployRecordId
     */
    public void setRecordId(Long recordId) {
        this.deployRecordId = recordId;
    }

    public Long getRecordId() {
        return this.deployRecordId;
    }

    /**
     * 兼容字段：映射到 stepOrder
     */
    public void setStepNo(Integer stepNo) {
        this.stepOrder = stepNo;
    }

    public Integer getStepNo() {
        return this.stepOrder;
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
