package com.opspilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_server")
public class Server {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String serverName;
    private String hostname;
    private Integer port;
    private String sshUsername;
    private Integer envType;
    private String osType;
    private String osVersion;
    private Integer cpuCores;
    private Integer memoryMb;
    private Integer diskTotalGb;
    private Integer sshKeyStatus;
    private LocalDateTime lastDetectTime;
    private String lastDetectResult;
    private Integer status;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /** JDK 版本列表（JSON 数组字符串） */
    private String jdkVersions;
    /** Node.js 版本列表（JSON 数组字符串） */
    private String nodeVersions;
}
