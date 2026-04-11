package com.opspilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_deploy_schedule")
public class DeploySchedule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String scheduleName;
    private Long moduleId;
    private String instanceIds;
    private String targetBranch;
    private String cronExpression;
    private String timezone;
    private Integer rollbackStrategy;
    private Integer healthCheckTimeout;
    private Integer healthCheckInterval;
    private Integer healthCheckRetries;
    private Integer dingtalkEnabled;
    private String dingtalkWebhook;
    private String dingtalkSecret;
    private String dingtalkAtUserIds;
    private Integer notifyOnSuccess;
    private Integer notifyOnFailure;
    private Long creatorId;
    private Integer status;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
