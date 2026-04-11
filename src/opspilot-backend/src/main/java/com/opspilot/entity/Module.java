package com.opspilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_module")
public class Module {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String moduleName;
    private Integer moduleType;
    private String repoUrl;
    private String repoBranch;
    private String repoPath;
    private String buildTool;
    private String buildCommand;
    private String artifactPath;
    private Integer status;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
