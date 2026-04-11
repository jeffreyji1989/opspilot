package com.opspilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String module;
    private String operation;
    private String targetType;
    private Long targetId;
    private String targetName;
    private String requestMethod;
    private String requestUri;
    private String requestParams;
    private Integer responseStatus;
    private String ipAddress;
    private String userAgent;
    private String errorMessage;
    private Integer durationMs;
    private LocalDateTime createTime;
}
