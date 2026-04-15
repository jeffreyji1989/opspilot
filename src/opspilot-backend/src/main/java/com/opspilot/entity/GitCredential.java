package com.opspilot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Git 认证实体，对应数据库表 t_git_credential
 *
 * <p>SSH Key / HTTPS Token 认证信息，敏感数据 AES-256-GCM 加密存储。</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@Data
@TableName("t_git_credential")
public class GitCredential {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String credentialName;
    /** 认证类型：0-SSH私钥, 1-Personal Access Token, 2-用户名密码 */
    private Integer credentialType;
    private String username;
    /** 加密后的凭证数据（AES-256-GCM，Base64 编码） */
    private String encryptedData;
    /** SSH Key 指纹（SHA256） */
    private String fingerprint;
    /** 适用域名 */
    private String domain;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedTime;
    private Integer status;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
