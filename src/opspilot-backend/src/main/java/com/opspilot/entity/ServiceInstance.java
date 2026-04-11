package com.opspilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_service_instance")
public class ServiceInstance {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long moduleId;
    private Long serverId;
    private String instanceName;
    private String deployPath;
    private Integer listenPort;
    private String healthCheckPath;
    private Integer healthCheckPort;
    private String runtimeType;
    private String runtimeVersion;
    private String jvmOptions;
    private String startCommand;
    private String currentVersion;
    private Integer pid;
    private Integer processStatus;
    private Integer status;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
