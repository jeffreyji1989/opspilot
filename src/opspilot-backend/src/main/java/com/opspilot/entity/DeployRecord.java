package com.opspilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

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
    private Integer deployType;
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
}
