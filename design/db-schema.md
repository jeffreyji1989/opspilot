# OpsPilot 数据库表设计

> 技术栈：MySQL 8.0 + Spring Boot 3 + MyBatis Plus  
> 字符集：utf8mb4 / 排序规则：utf8mb4_unicode_ci  
> 引擎：InnoDB  
> 日期：2026-04-12

---

## 全局约定

| 约定项 | 规则 |
|--------|------|
| 主键 | `id` BIGINT AUTO_INCREMENT |
| 审计字段 | `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP |
| 逻辑删除 | `deleted` TINYINT NOT NULL DEFAULT 0（0=未删除，1=已删除） |
| 字符集 | utf8mb4, utf8mb4_unicode_ci |
| 表名前缀 | `t_` |

---

## 1. t_user — 用户表

登录认证、操作人记录。

```sql
CREATE TABLE `t_user` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username`        VARCHAR(64)  NOT NULL                COMMENT '用户名（登录账号）',
  `password_hash`   VARCHAR(128) NOT NULL                COMMENT '密码哈希（BCrypt）',
  `display_name`    VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '显示名称',
  `email`           VARCHAR(128) NOT NULL DEFAULT ''     COMMENT '邮箱',
  `phone`           VARCHAR(20)  NOT NULL DEFAULT ''     COMMENT '手机号',
  `dingtalk_user_id` VARCHAR(64) NOT NULL DEFAULT ''     COMMENT '钉钉 UserID（用于通知/免登）',
  `role`            TINYINT      NOT NULL DEFAULT 0     COMMENT '角色：0=普通用户，1=管理员，2=超级管理员',
  `status`          TINYINT      NOT NULL DEFAULT 1     COMMENT '状态：0=禁用，1=启用',
  `last_login_time` DATETIME     NULL                    COMMENT '最后登录时间',
  `last_login_ip`   VARCHAR(45)  NULL                    COMMENT '最后登录IP（IPv6最长45字符）',
  `token_secret`    VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT 'Token密钥（重置时刷新，用于强制踢人）',
  `deleted`         TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=未删除，1=已删除',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_dingtalk_user_id` (`dingtalk_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
```

---

## 2. t_project — 项目表

负责人、所属业务线、标签。

```sql
CREATE TABLE `t_project` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_code`    VARCHAR(64)  NOT NULL                COMMENT '项目编码（唯一标识，如 OPS-DEPLOY）',
  `project_name`    VARCHAR(128) NOT NULL                COMMENT '项目名称',
  `description`     VARCHAR(512) NOT NULL DEFAULT ''     COMMENT '项目描述',
  `owner_id`        BIGINT       NOT NULL                COMMENT '负责人ID（关联 t_user.id）',
  `business_line`   VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '所属业务线（如：交易中台、供应链、CRM）',
  `tags`            VARCHAR(512) NOT NULL DEFAULT ''     COMMENT '标签（逗号分隔，如：Java,SpringBoot,微服务）',
  `status`          TINYINT      NOT NULL DEFAULT 1     COMMENT '状态：0=停用，1=启用',
  `deleted`         TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=未删除，1=已删除',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_code` (`project_code`),
  KEY `idx_owner_id` (`owner_id`),
  KEY `idx_business_line` (`business_line`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目表';
```

---

## 3. t_module — 模块表

项目下的子模块，支持多仓库 / Monorepo / Maven 多模块。

```sql
CREATE TABLE `t_module` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `project_id`      BIGINT        NOT NULL                COMMENT '所属项目ID（关联 t_project.id）',
  `module_name`     VARCHAR(128)  NOT NULL                COMMENT '模块名称（如：user-service、gateway）',
  `module_type`     TINYINT       NOT NULL DEFAULT 0     COMMENT '模块类型：0=独立仓库，1=Monorepo子模块，2=Maven多模块',
  `repo_url`        VARCHAR(512)  NOT NULL                COMMENT 'Git 仓库地址（Monorepo 时为总仓库地址）',
  `repo_branch`     VARCHAR(128)  NOT NULL DEFAULT 'main' COMMENT '默认分支',
  `repo_path`       VARCHAR(512)  NOT NULL DEFAULT ''     COMMENT '仓库内子路径（Monorepo/Maven 模块的相对路径，独立仓库为空）',
  `build_tool`      VARCHAR(32)   NOT NULL DEFAULT 'maven' COMMENT '构建工具：maven / gradle / npm / other',
  `build_command`   VARCHAR(512)  NOT NULL DEFAULT ''     COMMENT '自定义构建命令（留空则使用默认）',
  `artifact_path`   VARCHAR(512)  NOT NULL DEFAULT ''     COMMENT '构建产物路径（如：target/xxx.jar、dist/）',
  `status`          TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：0=停用，1=启用',
  `deleted`         TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=未删除，1=已删除',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_module` (`project_id`, `module_name`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_repo_url` (`repo_url`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模块表';
```

---

## 4. t_server — 服务器表

环境类型、SSH 公钥状态、自动探测信息。

```sql
CREATE TABLE `t_server` (
  `id`                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `server_name`         VARCHAR(128)  NOT NULL                COMMENT '服务器名称/别名',
  `hostname`            VARCHAR(255)  NOT NULL                COMMENT '主机名或IP地址',
  `port`                INT           NOT NULL DEFAULT 22     COMMENT 'SSH 端口',
  `env_type`            TINYINT       NOT NULL DEFAULT 0     COMMENT '环境类型：0=开发，1=测试，2=预发，3=生产',
  `os_type`             VARCHAR(32)   NOT NULL DEFAULT ''     COMMENT '操作系统类型（如：CentOS 7、Ubuntu 22.04）',
  `os_version`          VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '操作系统版本',
  `cpu_cores`           INT           NOT NULL DEFAULT 0     COMMENT 'CPU 核数（自动探测）',
  `memory_mb`           INT           NOT NULL DEFAULT 0     COMMENT '内存大小 MB（自动探测）',
  `disk_total_gb`       INT           NOT NULL DEFAULT 0     COMMENT '磁盘总量 GB（自动探测）',
  `ssh_key_status`      TINYINT       NOT NULL DEFAULT 0     COMMENT 'SSH 公钥状态：0=未配置，1=已配置，2=连接失败',
  `last_detect_time`    DATETIME      NULL                    COMMENT '最后一次自动探测时间',
  `last_detect_result`  VARCHAR(512)  NOT NULL DEFAULT ''     COMMENT '最后一次探测结果摘要（JSON）',
  `status`              TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：0=下线，1=在线',
  `deleted`             TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=未删除，1=已删除',
  `create_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_server_name` (`server_name`),
  KEY `idx_hostname` (`hostname`),
  KEY `idx_env_type` (`env_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务器表';
```

---

## 5. t_git_credential — Git 认证表

SSH Key / Token，AES 加密存储。

```sql
CREATE TABLE `t_git_credential` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `credential_name` VARCHAR(128)  NOT NULL                COMMENT '凭证名称（标识用途）',
  `credential_type` TINYINT       NOT NULL DEFAULT 0     COMMENT '认证类型：0=SSH 私钥，1=Personal Access Token，2=用户名密码',
  `username`        VARCHAR(128)  NOT NULL DEFAULT ''     COMMENT '用户名（Token 方式可留空）',
  `encrypted_data`  TEXT          NOT NULL                COMMENT '加密后的凭证数据（AES-256-GCM，Base64 编码）',
  `fingerprint`     VARCHAR(128)  NOT NULL DEFAULT ''     COMMENT 'SSH Key 指纹（SHA256，仅 SSH 类型有效）',
  `expires_at`      DATETIME      NULL                    COMMENT '过期时间（Token 类型适用，NULL=永不过期）',
  `last_used_time`  DATETIME      NULL                    COMMENT '最后使用时间',
  `status`          TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：0=禁用，1=启用',
  `deleted`         TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=未删除，1=已删除',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_credential_name` (`credential_name`),
  KEY `idx_credential_type` (`credential_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Git 认证表';
```

---

## 6. t_service_instance — 服务实例表

部署路径、端口、运行时版本、JVM 参数。

```sql
CREATE TABLE `t_service_instance` (
  `id`                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `module_id`           BIGINT        NOT NULL                COMMENT '所属模块ID（关联 t_module.id）',
  `server_id`           BIGINT        NOT NULL                COMMENT '所属服务器ID（关联 t_server.id）',
  `instance_name`       VARCHAR(128)  NOT NULL                COMMENT '实例名称（如：user-service-prod-01）',
  `deploy_path`         VARCHAR(512)  NOT NULL                COMMENT '部署路径（服务器上 Jar/应用的绝对路径）',
  `listen_port`         INT           NOT NULL                COMMENT '服务监听端口',
  `health_check_path`   VARCHAR(256)  NOT NULL DEFAULT ''     COMMENT '健康检查路径（如：/actuator/health，为空则用端口探测）',
  `health_check_port`   INT           NULL                    COMMENT '健康检查端口（默认同 listen_port，某些场景可能不同）',
  `runtime_type`        VARCHAR(32)   NOT NULL DEFAULT 'java' COMMENT '运行时类型：java / node / python / other',
  `runtime_version`     VARCHAR(32)   NOT NULL DEFAULT ''     COMMENT '运行时版本（如：17.0.9、21.0.1）',
  `jvm_options`         VARCHAR(2048) NOT NULL DEFAULT ''     COMMENT 'JVM 启动参数（如：-Xms512m -Xmx1024m -XX:+UseG1GC）',
  `start_command`       VARCHAR(512)  NOT NULL DEFAULT ''     COMMENT '自定义启动命令（留空则使用默认 java -jar）',
  `pid`                 INT           NULL                    COMMENT '当前进程 PID（运行时更新）',
  `process_status`      TINYINT       NOT NULL DEFAULT 0     COMMENT '进程状态：0=停止，1=运行中，2=启动中，3=停止中',
  `status`              TINYINT       NOT NULL DEFAULT 1     COMMENT '实例状态：0=下线，1=上线',
  `deleted`             TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=未删除，1=已删除',
  `create_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_instance_name` (`instance_name`),
  KEY `idx_module_id` (`module_id`),
  KEY `idx_server_id` (`server_id`),
  KEY `idx_process_status` (`process_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务实例表';
```

---

## 7. t_deploy_record — 发版记录表

版本号、分支、操作人、状态、耗时。

```sql
CREATE TABLE `t_deploy_record` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `module_id`         BIGINT        NOT NULL                COMMENT '所属模块ID（关联 t_module.id）',
  `instance_id`       BIGINT        NOT NULL                COMMENT '目标实例ID（关联 t_service_instance.id）',
  `deploy_no`         VARCHAR(64)   NOT NULL                COMMENT '发版流水号（唯一，如：DEP-20260412-001）',
  `version`           VARCHAR(64)   NOT NULL                COMMENT '版本号（如：1.2.3 或 Git Commit Hash）',
  `git_branch`        VARCHAR(128)  NOT NULL                COMMENT 'Git 分支名（如：main、release/1.2.0）',
  `git_commit`        VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT 'Git Commit Hash',
  `deploy_type`       TINYINT       NOT NULL DEFAULT 0     COMMENT '发版类型：0=手动发版，1=定时发版，2=回滚发版',
  `schedule_id`       BIGINT        NULL                    COMMENT '关联定时任务ID（关联 t_deploy_schedule.id，手动发版为 NULL）',
  `operator_id`       BIGINT        NOT NULL                COMMENT '操作人ID（关联 t_user.id）',
  `status`            TINYINT       NOT NULL DEFAULT 0     COMMENT '状态：0=等待中，1=拉取代码中，2=构建中，3=部署中，4=健康检查中，5=成功，6=失败，7=已回滚',
  `rollback_from_id`  BIGINT        NULL                    COMMENT '回滚来源记录ID（回滚时记录从哪个版本回滚）',
  `start_time`        DATETIME      NULL                    COMMENT '开始时间',
  `end_time`          DATETIME      NULL                    COMMENT '结束时间',
  `duration_seconds`  INT           NOT NULL DEFAULT 0     COMMENT '总耗时（秒）',
  `error_message`     VARCHAR(1024) NOT NULL DEFAULT ''     COMMENT '失败原因/错误信息',
  `deleted`           TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=未删除，1=已删除',
  `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_deploy_no` (`deploy_no`),
  KEY `idx_module_id` (`module_id`),
  KEY `idx_instance_id` (`instance_id`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_schedule_id` (`schedule_id`),
  KEY `idx_version` (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发版记录表';
```

---

## 8. t_deploy_step — 发版步骤明细表

拉代码 / 构建 / 部署 / 健康检查 各步骤状态。

```sql
CREATE TABLE `t_deploy_step` (
  `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `deploy_record_id`  BIGINT        NOT NULL                COMMENT '所属发版记录ID（关联 t_deploy_record.id）',
  `step_name`         VARCHAR(64)   NOT NULL                COMMENT '步骤名称：pull_code / build / deploy / health_check',
  `step_order`        INT           NOT NULL                COMMENT '步骤顺序（1,2,3,4...）',
  `status`            TINYINT       NOT NULL DEFAULT 0     COMMENT '步骤状态：0=未开始，1=执行中，2=成功，3=失败，4=跳过',
  `start_time`        DATETIME      NULL                    COMMENT '步骤开始时间',
  `end_time`          DATETIME      NULL                    COMMENT '步骤结束时间',
  `duration_seconds`  INT           NOT NULL DEFAULT 0     COMMENT '步骤耗时（秒）',
  `log_output`        TEXT          NULL                    COMMENT '步骤日志输出（构建日志/部署日志等）',
  `error_message`     VARCHAR(1024) NOT NULL DEFAULT ''     COMMENT '错误信息（失败时记录）',
  `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_deploy_record_id` (`deploy_record_id`),
  KEY `idx_step_name` (`step_name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发版步骤明细表';
```

---

## 9. t_deploy_schedule — 定时发版任务表

Cron、目标分支、回退策略、钉钉通知。

```sql
CREATE TABLE `t_deploy_schedule` (
  `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `schedule_name`         VARCHAR(128)  NOT NULL                COMMENT '定时任务名称',
  `module_id`             BIGINT        NOT NULL                COMMENT '所属模块ID（关联 t_module.id）',
  `instance_ids`          VARCHAR(1024) NOT NULL                COMMENT '目标实例ID列表（逗号分隔，关联 t_service_instance.id）',
  `target_branch`         VARCHAR(128)  NOT NULL DEFAULT 'main' COMMENT '目标部署分支',
  `cron_expression`       VARCHAR(64)   NOT NULL                COMMENT 'Cron 表达式（如：0 2 * * * 每天凌晨2点）',
  `timezone`              VARCHAR(32)   NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '时区',
  `rollback_strategy`     TINYINT       NOT NULL DEFAULT 0     COMMENT '回退策略：0=不自动回退，1=失败自动回退到上一版本，2=健康检查失败自动回退',
  `health_check_timeout`  INT           NOT NULL DEFAULT 60    COMMENT '健康检查超时时间（秒）',
  `health_check_interval` INT           NOT NULL DEFAULT 5     COMMENT '健康检查重试间隔（秒）',
  `health_check_retries`  INT           NOT NULL DEFAULT 10    COMMENT '健康检查最大重试次数',
  `dingtalk_enabled`      TINYINT       NOT NULL DEFAULT 1     COMMENT '是否启用钉钉通知：0=关闭，1=开启',
  `dingtalk_webhook`      VARCHAR(512)  NOT NULL DEFAULT ''     COMMENT '钉钉机器人 Webhook URL',
  `dingtalk_secret`       VARCHAR(128)  NOT NULL DEFAULT ''     COMMENT '钉钉加签密钥（AES 加密）',
  `dingtalk_at_user_ids`  VARCHAR(512)  NOT NULL DEFAULT ''     COMMENT '钉钉通知 @用户 ID列表（逗号分隔）',
  `notify_on_success`     TINYINT       NOT NULL DEFAULT 1     COMMENT '成功时通知：0=否，1=是',
  `notify_on_failure`     TINYINT       NOT NULL DEFAULT 1     COMMENT '失败时通知：0=否，1=是',
  `creator_id`            BIGINT        NOT NULL                COMMENT '创建人ID（关联 t_user.id）',
  `status`                TINYINT       NOT NULL DEFAULT 0     COMMENT '任务状态：0=暂停，1=运行中',
  `deleted`               TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=未删除，1=已删除',
  `create_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule_name` (`schedule_name`),
  KEY `idx_module_id` (`module_id`),
  KEY `idx_creator_id` (`creator_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时发版任务表';
```

---

## 10. t_operation_log — 操作日志表

记录所有用户的操作行为。

```sql
CREATE TABLE `t_operation_log` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`         BIGINT        NOT NULL                COMMENT '操作人ID（关联 t_user.id）',
  `module`          VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '操作模块（如：user、project、deploy、server）',
  `operation`       VARCHAR(64)   NOT NULL                COMMENT '操作类型（如：CREATE、UPDATE、DELETE、DEPLOY、ROLLBACK、LOGIN）',
  `target_type`     VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '目标类型（如：User、Project、Module、Server、Instance）',
  `target_id`       BIGINT        NULL                    COMMENT '目标记录ID',
  `target_name`     VARCHAR(256)  NOT NULL DEFAULT ''     COMMENT '目标名称/描述',
  `request_method`  VARCHAR(16)   NOT NULL DEFAULT ''     COMMENT 'HTTP 请求方法（GET/POST/PUT/DELETE）',
  `request_uri`     VARCHAR(512)  NOT NULL DEFAULT ''     COMMENT '请求 URI',
  `request_params`  TEXT          NULL                    COMMENT '请求参数（JSON 格式）',
  `response_status` INT           NOT NULL DEFAULT 0     COMMENT 'HTTP 响应状态码',
  `ip_address`      VARCHAR(45)   NOT NULL DEFAULT ''     COMMENT '操作人IP地址',
  `user_agent`      VARCHAR(512)  NOT NULL DEFAULT ''     COMMENT '浏览器/客户端 UA',
  `error_message`   VARCHAR(1024) NOT NULL DEFAULT ''     COMMENT '错误信息（异常时记录）',
  `duration_ms`     INT           NOT NULL DEFAULT 0     COMMENT '请求耗时（毫秒）',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_module` (`module`),
  KEY `idx_operation` (`operation`),
  KEY `idx_target` (`target_type`, `target_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';
```

---

## 11. t_alert_rule — 告警规则表（可选）

服务器监控告警规则。

```sql
CREATE TABLE `t_alert_rule` (
  `id`                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_name`           VARCHAR(128)  NOT NULL                COMMENT '告警规则名称',
  `server_id`           BIGINT        NOT NULL                COMMENT '关联服务器ID（关联 t_server.id，0=全局规则）',
  `metric_type`         VARCHAR(32)   NOT NULL                COMMENT '监控指标类型：cpu_usage / memory_usage / disk_usage / process_down / response_time',
  `operator`            VARCHAR(8)    NOT NULL DEFAULT '>'    COMMENT '比较运算符：> / < / >= / <= / ==',
  `threshold`           DECIMAL(10,2) NOT NULL                COMMENT '告警阈值',
  `duration_seconds`    INT           NOT NULL DEFAULT 60    COMMENT '持续时长（超过该时长才触发告警，防抖动）',
  `severity`            TINYINT       NOT NULL DEFAULT 1     COMMENT '告警级别：0=提示，1=警告，2=严重，3=紧急',
  `dingtalk_enabled`    TINYINT       NOT NULL DEFAULT 1     COMMENT '是否启用钉钉通知：0=关闭，1=开启',
  `dingtalk_webhook`    VARCHAR(512)  NOT NULL DEFAULT ''     COMMENT '钉钉机器人 Webhook URL',
  `dingtalk_secret`     VARCHAR(128)  NOT NULL DEFAULT ''     COMMENT '钉钉加签密钥（AES 加密）',
  `message_template`    VARCHAR(512)  NOT NULL DEFAULT ''     COMMENT '告警消息模板（支持变量：{server_name}, {metric}, {value}, {threshold}）',
  `enabled`             TINYINT       NOT NULL DEFAULT 1     COMMENT '是否启用：0=禁用，1=启用',
  `cooldown_seconds`    INT           NOT NULL DEFAULT 300   COMMENT '告警冷却时间（秒），同一规则在冷却期内不重复告警',
  `creator_id`          BIGINT        NOT NULL                COMMENT '创建人ID（关联 t_user.id）',
  `deleted`             TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0=未删除，1=已删除',
  `create_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_server_id` (`server_id`),
  KEY `idx_metric_type` (`metric_type`),
  KEY `idx_severity` (`severity`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警规则表';
```

---

## 12. t_server_monitor_log — 服务器监控数据表（可选）

定期采集的服务器性能指标。

```sql
CREATE TABLE `t_server_monitor_log` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `server_id`       BIGINT        NOT NULL                COMMENT '服务器ID（关联 t_server.id）',
  `cpu_usage`       DECIMAL(5,2)  NOT NULL DEFAULT 0     COMMENT 'CPU 使用率（0.00~100.00）',
  `memory_usage`    DECIMAL(5,2)  NOT NULL DEFAULT 0     COMMENT '内存使用率（0.00~100.00）',
  `memory_used_mb`  INT           NOT NULL DEFAULT 0     COMMENT '已用内存 MB',
  `memory_total_mb` INT           NOT NULL DEFAULT 0     COMMENT '总内存 MB',
  `disk_usage`      DECIMAL(5,2)  NOT NULL DEFAULT 0     COMMENT '磁盘使用率（0.00~100.00）',
  `disk_used_gb`    DECIMAL(8,2)  NOT NULL DEFAULT 0     COMMENT '已用磁盘 GB',
  `disk_total_gb`   DECIMAL(8,2)  NOT NULL DEFAULT 0     COMMENT '总磁盘 GB',
  `load_avg_1m`     DECIMAL(5,2)  NOT NULL DEFAULT 0     COMMENT '1 分钟平均负载',
  `load_avg_5m`     DECIMAL(5,2)  NOT NULL DEFAULT 0     COMMENT '5 分钟平均负载',
  `load_avg_15m`    DECIMAL(5,2)  NOT NULL DEFAULT 0     COMMENT '15 分钟平均负载',
  `network_in_kbps` DECIMAL(10,2) NOT NULL DEFAULT 0     COMMENT '网络入站速率 KB/s',
  `network_out_kbps`DECIMAL(10,2) NOT NULL DEFAULT 0     COMMENT '网络出站速率 KB/s',
  `uptime_days`     INT           NOT NULL DEFAULT 0     COMMENT '系统运行天数',
  `process_count`   INT           NOT NULL DEFAULT 0     COMMENT '当前进程数',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '采集时间',
  PRIMARY KEY (`id`),
  KEY `idx_server_id` (`server_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_server_time` (`server_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务器监控数据表';
```

---

## ER 关系图（文本）

```
t_user (用户)
  ├── 1:N → t_project.owner_id (项目负责)
  ├── 1:N → t_deploy_schedule.creator_id (定时任务创建人)
  ├── 1:N → t_deploy_record.operator_id (发版操作人)
  ├── 1:N → t_alert_rule.creator_id (告警规则创建人)
  └── 1:N → t_operation_log.user_id (操作日志)

t_project (项目)
  └── 1:N → t_module.project_id (项目模块)

t_module (模块)
  ├── 1:N → t_service_instance.module_id (服务实例)
  └── 1:N → t_deploy_record.module_id (发版记录)
  └── 1:N → t_deploy_schedule.module_id (定时发版任务)

t_server (服务器)
  ├── 1:N → t_service_instance.server_id (服务实例部署位置)
  ├── 1:N → t_alert_rule.server_id (告警规则)
  └── 1:N → t_server_monitor_log.server_id (监控数据)

t_service_instance (服务实例)
  └── 1:N → t_deploy_record.instance_id (发版记录)

t_deploy_schedule (定时发版任务)
  └── 1:N → t_deploy_record.schedule_id (发版记录)

t_deploy_record (发版记录)
  └── 1:N → t_deploy_step.deploy_record_id (发版步骤)

t_alert_rule (告警规则)  -- 监控数据超阈值时触发，与 t_server_monitor_log 联动
t_server_monitor_log (监控日志)  -- 定时采集，不关联业务表
```

---

## 索引设计说明

| 表 | 索引策略 | 说明 |
|----|---------|------|
| t_user | uk_username, uk_dingtalk_user_id | 登录查询唯一索引 |
| t_project | uk_project_code, idx_owner_id | 按负责人/业务线查询 |
| t_module | uk_project_module, idx_project_id, idx_repo_url | 项目-模块唯一约束 |
| t_server | uk_server_name, idx_env_type | 按环境类型筛选 |
| t_git_credential | uk_credential_name | 凭证名称唯一 |
| t_service_instance | uk_instance_name, idx_module_id, idx_server_id | 按模块/服务器查实例 |
| t_deploy_record | uk_deploy_no, 多索引 | 发版列表查询（状态、时间、版本） |
| t_deploy_step | idx_deploy_record_id | 按发版记录查步骤 |
| t_deploy_schedule | uk_schedule_name, idx_status | 定时任务列表 |
| t_operation_log | 多索引 | 按用户/模块/时间范围查询 |
| t_alert_rule | idx_server_id, idx_metric_type | 按服务器/指标类型查规则 |
| t_server_monitor_log | idx_server_time | 复合索引，按服务器+时间范围查询监控趋势 |

---

## MyBatis Plus 实体映射建议

```java
// 通用基类
@MappedSuperclass
@Data
public abstract class BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

// 带逻辑删除的基类
@MappedSuperclass
@Data
public abstract class BaseDeletedEntity extends BaseEntity {
    @TableLogic
    private Integer deleted;
}
```

> **注意**：`t_operation_log` 和 `t_server_monitor_log` 不设 `deleted` 字段（日志表不做逻辑删除，可按时间归档清理）。
