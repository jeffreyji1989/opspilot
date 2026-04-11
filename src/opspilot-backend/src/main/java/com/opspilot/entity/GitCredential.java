package com.opspilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_git_credential")
public class GitCredential {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String credentialName;
    private Integer credentialType;
    private String username;
    private String encryptedData;
    private String fingerprint;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedTime;
    private Integer status;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
