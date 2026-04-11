-- OpsPilot Database Initialization
-- Create database
CREATE DATABASE IF NOT EXISTS opspilot DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE opspilot;

-- 1. t_user
CREATE TABLE IF NOT EXISTS `t_user` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username`        VARCHAR(64)  NOT NULL                COMMENT '用户名',
  `password_hash`   VARCHAR(128) NOT NULL                COMMENT '密码哈希(BCrypt)',
  `display_name`    VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '显示名称',
  `email`           VARCHAR(128) NOT NULL DEFAULT ''     COMMENT '邮箱',
  `phone`           VARCHAR(20)  NOT NULL DEFAULT ''     COMMENT '手机号',
  `dingtalk_user_id` VARCHAR(64) NOT NULL DEFAULT ''     COMMENT '钉钉UserID',
  `role`            TINYINT      NOT NULL DEFAULT 0     COMMENT '角色：0=普通用户，1=管理员，2=超级管理员',
  `status`          TINYINT      NOT NULL DEFAULT 1     COMMENT '状态：0=禁用，1=启用',
  `last_login_time` DATETIME     NULL                    COMMENT '最后登录时间',
  `last_login_ip`   VARCHAR(45)  NULL                    COMMENT '最后登录IP',
  `token_secret`    VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT 'Token密钥',
  `deleted`         TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- Insert default admin user (password: admin123)
INSERT INTO `t_user` (`username`, `password_hash`, `display_name`, `role`, `status`) VALUES
('admin', '$2a$10$MgZv3/v6CRvbjfzJgqL1XOj23g6XiY75bCe7QUTAg05JHN1HQBExK', '系统管理员', 2, 1);

-- 2. t_project
CREATE TABLE IF NOT EXISTS `t_project` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `project_code`    VARCHAR(64)  NOT NULL,
  `project_name`    VARCHAR(128) NOT NULL,
  `description`     VARCHAR(512) NOT NULL DEFAULT '',
  `owner_id`        BIGINT       NOT NULL,
  `business_line`   VARCHAR(64)  NOT NULL DEFAULT '',
  `tags`            VARCHAR(512) NOT NULL DEFAULT '',
  `status`          TINYINT      NOT NULL DEFAULT 1,
  `deleted`         TINYINT      NOT NULL DEFAULT 0,
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_code` (`project_code`),
  KEY `idx_owner_id` (`owner_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目表';

-- 3. t_module
CREATE TABLE IF NOT EXISTS `t_module` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT,
  `project_id`      BIGINT        NOT NULL,
  `module_name`     VARCHAR(128)  NOT NULL,
  `module_type`     TINYINT       NOT NULL DEFAULT 0,
  `repo_url`        VARCHAR(512)  NOT NULL,
  `repo_branch`     VARCHAR(128)  NOT NULL DEFAULT 'main',
  `repo_path`       VARCHAR(512)  NOT NULL DEFAULT '',
  `build_tool`      VARCHAR(32)   NOT NULL DEFAULT 'maven',
  `build_command`   VARCHAR(512)  NOT NULL DEFAULT '',
  `artifact_path`   VARCHAR(512)  NOT NULL DEFAULT '',
  `status`          TINYINT       NOT NULL DEFAULT 1,
  `deleted`         TINYINT       NOT NULL DEFAULT 0,
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_module` (`project_id`, `module_name`),
  KEY `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模块表';

-- 4. t_server
CREATE TABLE IF NOT EXISTS `t_server` (
  `id`                  BIGINT        NOT NULL AUTO_INCREMENT,
  `server_name`         VARCHAR(128)  NOT NULL,
  `hostname`            VARCHAR(255)  NOT NULL,
  `port`                INT           NOT NULL DEFAULT 22,
  `ssh_username`        VARCHAR(64)   NOT NULL DEFAULT 'root',
  `env_type`            TINYINT       NOT NULL DEFAULT 0,
  `os_type`             VARCHAR(32)   NOT NULL DEFAULT '',
  `os_version`          VARCHAR(64)   NOT NULL DEFAULT '',
  `cpu_cores`           INT           NOT NULL DEFAULT 0,
  `memory_mb`           INT           NOT NULL DEFAULT 0,
  `disk_total_gb`       INT           NOT NULL DEFAULT 0,
  `ssh_key_status`      TINYINT       NOT NULL DEFAULT 0,
  `last_detect_time`    DATETIME      NULL,
  `last_detect_result`  VARCHAR(512)  NOT NULL DEFAULT '',
  `status`              TINYINT       NOT NULL DEFAULT 1,
  `deleted`             TINYINT       NOT NULL DEFAULT 0,
  `create_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_server_name` (`server_name`),
  KEY `idx_env_type` (`env_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务器表';

-- 5. t_git_credential
CREATE TABLE IF NOT EXISTS `t_git_credential` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT,
  `credential_name` VARCHAR(128)  NOT NULL,
  `credential_type` TINYINT       NOT NULL DEFAULT 0,
  `username`        VARCHAR(128)  NOT NULL DEFAULT '',
  `encrypted_data`  TEXT          NOT NULL,
  `fingerprint`     VARCHAR(128)  NOT NULL DEFAULT '',
  `expires_at`      DATETIME      NULL,
  `last_used_time`  DATETIME      NULL,
  `status`          TINYINT       NOT NULL DEFAULT 1,
  `deleted`         TINYINT       NOT NULL DEFAULT 0,
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_credential_name` (`credential_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Git认证表';

-- 6. t_service_instance
CREATE TABLE IF NOT EXISTS `t_service_instance` (
  `id`                  BIGINT        NOT NULL AUTO_INCREMENT,
  `module_id`           BIGINT        NOT NULL,
  `server_id`           BIGINT        NOT NULL,
  `instance_name`       VARCHAR(128)  NOT NULL,
  `deploy_path`         VARCHAR(512)  NOT NULL,
  `listen_port`         INT           NOT NULL,
  `health_check_path`   VARCHAR(256)  NOT NULL DEFAULT '',
  `health_check_port`   INT           NULL,
  `runtime_type`        VARCHAR(32)   NOT NULL DEFAULT 'java',
  `runtime_version`     VARCHAR(32)   NOT NULL DEFAULT '',
  `jvm_options`         VARCHAR(2048) NOT NULL DEFAULT '',
  `start_command`       VARCHAR(512)  NOT NULL DEFAULT '',
  `current_version`     VARCHAR(64)   NULL,
  `pid`                 INT           NULL,
  `process_status`      TINYINT       NOT NULL DEFAULT 0,
  `status`              TINYINT       NOT NULL DEFAULT 1,
  `deleted`             TINYINT       NOT NULL DEFAULT 0,
  `create_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_instance_name` (`instance_name`),
  KEY `idx_module_id` (`module_id`),
  KEY `idx_server_id` (`server_id`),
  KEY `idx_process_status` (`process_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务实例表';

-- 7. t_deploy_record
CREATE TABLE IF NOT EXISTS `t_deploy_record` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT,
  `module_id`         BIGINT        NOT NULL,
  `instance_id`       BIGINT        NOT NULL,
  `deploy_no`         VARCHAR(64)   NOT NULL,
  `version`           VARCHAR(64)   NOT NULL,
  `git_branch`        VARCHAR(128)  NOT NULL,
  `git_commit`        VARCHAR(64)   NOT NULL DEFAULT '',
  `deploy_type`       TINYINT       NOT NULL DEFAULT 0,
  `schedule_id`       BIGINT        NULL,
  `operator_id`       BIGINT        NOT NULL,
  `status`            TINYINT       NOT NULL DEFAULT 0,
  `rollback_from_id`  BIGINT        NULL,
  `start_time`        DATETIME      NULL,
  `end_time`          DATETIME      NULL,
  `duration_seconds`  INT           NOT NULL DEFAULT 0,
  `error_message`     VARCHAR(1024) NOT NULL DEFAULT '',
  `deleted`           TINYINT       NOT NULL DEFAULT 0,
  `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_deploy_no` (`deploy_no`),
  KEY `idx_module_id` (`module_id`),
  KEY `idx_instance_id` (`instance_id`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发版记录表';

-- 8. t_deploy_step
CREATE TABLE IF NOT EXISTS `t_deploy_step` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT,
  `deploy_record_id`  BIGINT        NOT NULL,
  `step_name`         VARCHAR(64)   NOT NULL,
  `step_order`        INT           NOT NULL,
  `status`            TINYINT       NOT NULL DEFAULT 0,
  `start_time`        DATETIME      NULL,
  `end_time`          DATETIME      NULL,
  `duration_seconds`  INT           NOT NULL DEFAULT 0,
  `log_output`        TEXT          NULL,
  `error_message`     VARCHAR(1024) NOT NULL DEFAULT '',
  `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_deploy_record_id` (`deploy_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发版步骤明细表';

-- 9. t_deploy_schedule
CREATE TABLE IF NOT EXISTS `t_deploy_schedule` (
  `id`                    BIGINT        NOT NULL AUTO_INCREMENT,
  `schedule_name`         VARCHAR(128)  NOT NULL,
  `module_id`             BIGINT        NOT NULL,
  `instance_ids`          VARCHAR(1024) NOT NULL,
  `target_branch`         VARCHAR(128)  NOT NULL DEFAULT 'main',
  `cron_expression`       VARCHAR(64)   NOT NULL,
  `timezone`              VARCHAR(32)   NOT NULL DEFAULT 'Asia/Shanghai',
  `rollback_strategy`     TINYINT       NOT NULL DEFAULT 0,
  `health_check_timeout`  INT           NOT NULL DEFAULT 60,
  `health_check_interval` INT           NOT NULL DEFAULT 5,
  `health_check_retries`  INT           NOT NULL DEFAULT 10,
  `dingtalk_enabled`      TINYINT       NOT NULL DEFAULT 1,
  `dingtalk_webhook`      VARCHAR(512)  NOT NULL DEFAULT '',
  `dingtalk_secret`       VARCHAR(128)  NOT NULL DEFAULT '',
  `dingtalk_at_user_ids`  VARCHAR(512)  NOT NULL DEFAULT '',
  `notify_on_success`     TINYINT       NOT NULL DEFAULT 1,
  `notify_on_failure`     TINYINT       NOT NULL DEFAULT 1,
  `creator_id`            BIGINT        NOT NULL,
  `status`                TINYINT       NOT NULL DEFAULT 0,
  `deleted`               TINYINT       NOT NULL DEFAULT 0,
  `create_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule_name` (`schedule_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时发版任务表';

-- 10. t_operation_log
CREATE TABLE IF NOT EXISTS `t_operation_log` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT,
  `user_id`         BIGINT        NOT NULL,
  `module`          VARCHAR(64)   NOT NULL DEFAULT '',
  `operation`       VARCHAR(64)   NOT NULL,
  `target_type`     VARCHAR(64)   NOT NULL DEFAULT '',
  `target_id`       BIGINT        NULL,
  `target_name`     VARCHAR(256)  NOT NULL DEFAULT '',
  `request_method`  VARCHAR(16)   NOT NULL DEFAULT '',
  `request_uri`     VARCHAR(512)  NOT NULL DEFAULT '',
  `request_params`  TEXT          NULL,
  `response_status` INT           NOT NULL DEFAULT 0,
  `ip_address`      VARCHAR(45)   NOT NULL DEFAULT '',
  `user_agent`      VARCHAR(512)  NOT NULL DEFAULT '',
  `error_message`   VARCHAR(1024) NOT NULL DEFAULT '',
  `duration_ms`     INT           NOT NULL DEFAULT 0,
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_module` (`module`),
  KEY `idx_operation` (`operation`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';
