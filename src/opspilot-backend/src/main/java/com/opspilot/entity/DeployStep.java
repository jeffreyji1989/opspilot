package com.opspilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

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
}
