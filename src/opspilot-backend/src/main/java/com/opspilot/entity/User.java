package com.opspilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String displayName;
    private String email;
    private String phone;
    private String dingtalkUserId;
    private Integer role;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private String tokenSecret;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
