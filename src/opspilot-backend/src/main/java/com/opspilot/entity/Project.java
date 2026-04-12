package com.opspilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_project")
public class Project {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String projectCode;
    private String projectName;
    private String description;
    private Long ownerId;
    private String businessLine;
    private String tags;
    /** 运行时类型: java / node / python */
    private String runtimeType;
    /** 运行时版本: 如 8/11/17/21 */
    private String runtimeVersion;
    private Integer status;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
