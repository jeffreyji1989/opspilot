# OpsPilot 运维管理系统 — PRD 架构评审报告

> **评审人**: 技术架构师  
> **评审日期**: 2026-04-12  
> **评审对象**: `projects/opspilot/prd/opspilot-prd.md` v1.0  
> **评审结论**: **条件通过**（需修复中等级别问题后方可进入开发）

---

## 1. 技术可行性分析

### 1.1 整体技术栈评估：✅ 合理

| 层级 | 选型 | 评估 |
|------|------|------|
| 后端框架 | Spring Boot 3.x | ✅ 成熟稳定，LTS 版本支持至 2029，社区活跃。需 JDK 17+，符合企业主流升级趋势 |
| ORM | MyBatis Plus | ✅ 国内生态完善，CRUD 便捷，动态 SQL 灵活，适合运维系统场景 |
| 前端框架 | Vue 3 + Composition API | ✅ 当前主流，Element Plus 组件库成熟，开发效率高 |
| 数据库 | MySQL 8.x | ✅ 运维系统数据量可控（百/千级表记录），MySQL 8 足够支撑 |
| 缓存 | Redis | ✅ 用户 Session、部署锁、实时日志缓存等场景适用 |
| 认证 | 用户名 + 密码（JWT） | ⚠️ V1 无 RBAC，JWT 方案简单可行，但需预留扩展 |
| 实时通信 | WebSocket | ✅ 日志实时推送核心需求，Spring WebSocket + STOMP 成熟 |
| SSH 连接 | JSch / SSHJ | ⚠️ 见下方 1.3 详细分析 |
| 定时任务 | Spring Scheduled / Quartz | ⚠️ 两者选其一，需明确决策（见下方分析） |
| 告警 | 钉钉机器人 Webhook | ✅ 轻量，适合小团队，无额外依赖 |

### 1.2 Spring Boot 3 + Vue 3 + MySQL + Redis 组合合理性

**结论：技术栈选型合理，无明显风险。**

- **前后端分离**：Spring Boot REST API + Vue 3 SPA，标准架构，团队易于维护
- **MySQL 8**：支持窗口函数、CTE 等，对操作日志分析查询有利
- **Redis**：
  - 场景 1：用户 Session/Token 存储（分布式部署时必要）
  - 场景 2：部署任务分布式锁（并发部署控制）
  - 场景 3：实时日志暂存/订阅（发布订阅模式）
  - 场景 4：环境探测结果缓存（避免重复探测）
- **注意点**：团队规模 5-20 人，初期 Redis 可考虑单实例，无需集群

### 1.3 SSH 连接方案：JSch vs SSHJ

| 维度 | JSch | SSHJ |
|------|------|------|
| 维护状态 | ⚠️ 2016 年后基本停滞（0.1.55 之后更新缓慢） | ✅ 活跃维护，Java 原生 API |
| 算法支持 | ⚠️ 不支持 Ed25519（新 GitHub Key 默认格式） | ✅ 支持 Ed25519、现代加密算法 |
| 代码质量 | 中等 | 较好 |
| Spring Boot 集成 | 需要手动封装 | 需要手动封装 |
| 社区活跃度 | 低 | 中 |

**推荐：SSHJ**

理由：
1. 现代 Git 平台（GitHub/GitLab）默认生成 Ed25519 密钥，JSch 不支持
2. SSHJ 提供流式 API，更适配实时日志推送场景
3. JSch 虽可用但存在安全隐患（算法老化）

如必须使用 JSch（团队熟悉度），建议升级到其 fork 版本 `mwiede/jsch`（持续维护中）。

---

## 2. 数据库表设计评审

### 2.1 现有表概览

| 序号 | 表名 | 状态 | 评价 |
|------|------|------|------|
| 1 | `t_project` | ✅ 完整 | 设计合理 |
| 2 | `t_module` | ✅ 完整 | 设计合理 |
| 3 | `t_server` | ✅ 基本完整 | 缺少服务器分组字段 |
| 4 | `t_git_credential` | ✅ 完整 | 加密存储设计到位 |
| 5 | `t_service` | ✅ 完整 | 设计合理 |
| 6 | `t_deploy_record` | ⚠️ 有遗漏 | 见 2.2 |
| 7 | `t_operation_log` | ✅ 完整 | 设计合理 |
| 8 | `t_schedule_task` | ✅ 完整 | 设计合理 |
| 9 | `t_system_config` | ✅ 完整 | 设计合理 |

### 2.2 遗漏表/字段识别

#### ❌ 缺失：用户表 `t_user`

PRD 虽然声明 V1 "无细粒度角色权限"，但仍有登录认证需求。多处字段引用了 `operator`（操作人）但无对应用户表。

**建议新增**：

```sql
CREATE TABLE t_user (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    password    VARCHAR(256) NOT NULL COMMENT 'BCrypt 加密',
    real_name   VARCHAR(64)  NOT NULL COMMENT '真实姓名（用于显示操作人）',
    email       VARCHAR(128) COMMENT '邮箱（用于找回密码）',
    phone       VARCHAR(32)  COMMENT '手机号（钉钉通知 @用）',
    status      VARCHAR(16)  DEFAULT 'active' COMMENT 'active/disabled',
    last_login_time DATETIME COMMENT '最后登录时间',
    deleted     TINYINT      DEFAULT 0,
    create_time DATETIME     NOT NULL,
    update_time DATETIME     NOT NULL
) COMMENT '用户表';
```

> **影响**：`t_deploy_record.operator`、`t_operation_log.operator`、`t_service` 的创建人等字段需要与 `t_user` 关联。当前直接用 `VARCHAR(64)` 存储用户名虽然可行，但缺乏数据一致性保障。

#### ❌ 缺失：部署步骤明细表 `t_deploy_step`

发版流程有 7 个步骤，PRD 中 `t_deploy_record.deploy_log` 用 TEXT 存储全部日志，不利于：
- 查询某个具体步骤的成功率
- 统计哪个步骤最常失败
- 展示部署进度条时精确到步骤级别

**建议新增**：

```sql
CREATE TABLE t_deploy_step (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    deploy_record_id BIGINT NOT NULL COMMENT '关联发版记录',
    step_no         INT    NOT NULL COMMENT '步骤序号 1-7',
    step_name       VARCHAR(64) NOT NULL COMMENT '步骤名称',
    status          VARCHAR(16) NOT NULL COMMENT 'pending/running/success/failed/skipped',
    start_time      DATETIME COMMENT '步骤开始时间',
    end_time        DATETIME COMMENT '步骤结束时间',
    duration_seconds INT   COMMENT '步骤耗时',
    log_content     TEXT   COMMENT '步骤日志',
    error_message   TEXT   COMMENT '错误信息',
    create_time     DATETIME NOT NULL
) COMMENT '部署步骤明细表';
```

#### ⚠️ 缺失：`t_service` 缺少 `created_by` / `updated_by` 字段

PRD 中的 `t_service` 表缺少创建人/更新人字段，不利于审计。

#### ⚠️ 缺失：`t_server` 缺少服务器分组字段

PRD 3.4 中提到"分组管理"，但 `t_server` 表无 `group_name` 或 `group_id` 字段。

#### ⚠️ 缺失：`t_deploy_record` 缺少 `target_version_tag` 字段

发版时指定了目标分支/Tag/Commit，但只有 `git_branch` 和 `commit_id`，没有区分是分支还是 Tag。建议增加：

```sql
ref_type VARCHAR(16) COMMENT 'branch/tag/commit'
```

#### ⚠️ 缺失：`t_module` 缺少 `deploy_server_id` 字段

PRD 中提到"在服务器上拉代码编译"，但模块表没有绑定服务器。虽然服务实例表（`t_service`）绑定了服务器，但如果一个模块需要在多台服务器部署（如集群场景），编译在哪台机器上执行不明确。

**V1 可暂不处理**（单实例部署），但需在架构文档中注明限制。

### 2.3 索引建议

以下字段建议在 DDL 中添加索引：

| 表 | 字段 | 索引类型 | 理由 |
|----|------|----------|------|
| t_deploy_record | service_id | 普通索引 | 按服务查询发版历史 |
| t_deploy_record | status | 普通索引 | 按状态筛选 |
| t_operation_log | create_time | 普通索引 | 按时间范围查询 |
| t_operation_log | operator | 普通索引 | 按操作人查询 |
| t_schedule_task | next_exec_time | 普通索引 | 定时任务扫描 |
| t_schedule_task | status | 普通索引 | 查询待执行任务 |
| t_service | server_id | 普通索引 | 查询服务器上服务列表 |
| t_service | status | 普通索引 | 按状态筛选 |
| t_git_credential | domain | 普通索引 | 按域名匹配认证 |

---

## 3. 技术方案可行性评审

### 3.1 SSH 互信方案：⚠️ 可行但需注意安全性

**PRD 方案**：添加服务器时输入密码 → SSH 连接 → 自动部署公钥 → 密码不入库

**可行性：可行**

技术实现路径：
1. 系统生成一对 RSA/Ed25519 密钥对（存储在服务端）
2. 通过 SSH 密码连接目标服务器
3. 将公钥追加到目标服务器的 `~/.ssh/authorized_keys`
4. 验证公钥认证成功后，清除内存中的密码

**风险点**：
- 🔴 **系统私钥安全**：OpsPilot 服务端的私钥是"万能钥匙"，所有服务器都信任它。一旦泄露，所有服务器沦陷
- 🟡 **密码传输安全**：前端到后端传输 SSH 密码时必须使用 HTTPS
- 🟡 **authorized_keys 管理**：多次添加会追加多条公钥，缺少清理机制
- 🟢 **密码不入库**：设计正确

**改进建议**：
1. 系统私钥存储在服务端文件系统中，权限 `600`，路径不对外暴露
2. 增加"重新建立互信"功能（用于服务器重装后恢复）
3. 考虑支持用户自带 SSH Key（而非系统统一密钥），更安全
4. 添加互信时记录审计日志（哪些服务器在什么时间建立了信任）

### 3.2 Git 认证存储（AES 加密）：✅ 可行

**PRD 方案**：Git 私钥和 Token 使用 AES-256 加密存储在数据库中

**可行性：可行**

技术实现路径：
1. 使用 Spring 的 `AES/GCM/NoPadding` 模式（推荐 GCM 而非 ECB/CBC）
2. 加密密钥不硬编码，通过环境变量或配置文件外部化
3. 使用 `@ColumnTransformer` 或自定义 MyBatis TypeHandler 实现自动加解密

```java
// 示例：自定义 TypeHandler
public class EncryptedStringTypeHandler extends BaseTypeHandler<String> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) {
        ps.setString(i, AesGcmUtil.encrypt(parameter, encryptionKey));
    }
    // ...
}
```

**风险点**：
- 🟡 **加密密钥管理**：密钥如果和代码一起提交到 Git，则加密形同虚设
- 🟡 **AES-256 vs AES-128**：PRD 写的是 AES-256，实际 Java 的 AES/GCM 实现中 256 位需要 JCE Unlimited Strength，JDK 8u161+ 默认支持，Spring Boot 3 (JDK 17+) 无此问题

**改进建议**：
1. 加密密钥通过环境变量注入：`export OPSPILOT_AES_KEY=xxx`
2. 使用 HashiCorp Vault 或阿里云 KMS 管理密钥（后续迭代）
3. 定期轮换加密密钥（需要重新加密所有数据）

### 3.3 WebSocket 实时日志：✅ 可行

**PRD 方案**：WebSocket 连接 → 服务器执行 `tail -f` → 实时推送

**可行性：可行**

技术实现路径：
1. Spring WebSocket + STOMP 协议
2. 后端通过 SSHJ 执行 `tail -f /path/to/logs/*.log`，获取 `InputStream`
3. 将 InputStream 内容逐行通过 WebSocket 推送给前端
4. 前端使用 WebSocket 客户端接收并渲染

```java
// 伪代码
@MessageMapping("/log/{serviceId}")
public void streamLog(@DestinationVariable Long serviceId, SimpMessageHeaderAccessor accessor) {
    Session sshSession = sshManager.connect(service);
    try (Exec exec = sshSession.exec("tail -f logs/*.log")) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(exec.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null && !accessor.getSession().isClosed()) {
            template.convertAndSend("/topic/log/" + serviceId, line);
        }
    }
}
```

**风险点**：
- 🟡 **连接泄漏**：用户关闭页面后，`tail -f` 进程可能仍在服务器运行，需要连接断开时自动 kill
- 🟡 **日志文件过多**：`tail -f logs/*.log` 在日志文件多时可能打开大量文件描述符
- 🟢 **延迟 < 1 秒**：可行，WebSocket 推送 + `tail -f` 缓冲通常延迟在 100-500ms

**改进建议**：
1. 使用 `tail -n 1000 -f` 限制初始行数
2. 建立 WebSocket Session 与 SSH 进程的映射表，断开时自动 kill 远程进程
3. 日志级别过滤在后端实现（使用 `grep` 管道），减少网络传输
4. 考虑使用 `inotifywait` 或文件尾轮询替代 `tail -f`，更可控

### 3.4 Maven 多模块 `-pl -am` 编译方案：✅ 可行

**PRD 方案**：`mvn clean package -pl {mavenModuleName} -am -DskipTests`

**可行性：可行**

- `-pl`（--projects）：指定要构建的模块
- `-am`（--also-make）：同时构建指定模块的依赖模块
- 这是 Maven 标准功能，稳定可靠

**风险点**：
- 🟡 **Monorepo 场景**：如果仓库子路径和 Maven 模块名同时配置，需要 `cd` 到子路径后再执行 Maven 命令，目录切换逻辑需要正确处理
- 🟡 **构建产物定位**：`-pl` 编译后，产物在模块的 `target/` 目录下。如果子路径存在，需要正确拼接路径
- 🟡 **多 JDK 版本**：如果不同模块需要不同 JDK 版本，需要通过 `jenv` 或 `JAVA_HOME` 切换

**改进建议**：
1. 编译命令执行前校验 Maven 是否安装：`which mvn`
2. 构建产物路径支持相对路径和绝对路径两种配置
3. 增加构建超时控制（如 30 分钟），避免编译卡死
4. 支持自定义 Maven settings.xml（私库认证场景）

### 3.5 定时任务方案：⚠️ 需要明确选型

PRD 同时提到了 `Spring Scheduled` 和 `Quartz`，两者差异较大：

| 维度 | Spring Scheduled | Quartz |
|------|-------------------|--------|
| 动态调度 | ❌ Cron 表达式需硬编码或重启加载 | ✅ 支持运行时动态增删改 |
| 持久化 | ❌ 内存中，重启丢失 | ✅ 可持久化到数据库 |
| 集群支持 | ❌ 无 | ✅ 支持集群 |
| 复杂度 | 低 | 中 |
| 适用场景 | 固定周期任务 | 动态定时任务 |

**推荐：Quartz**

理由：
1. PRD 中定时发版任务需要"支持运行时动态创建/删除/修改"
2. 任务状态需要持久化（重启后可恢复）
3. 需要记录 `last_exec_time`、`next_exec_time` 等字段

Spring Scheduled 无法满足动态任务管理需求。

---

## 4. 风险点识别

### 🔴 高风险

| # | 风险项 | 影响 | 建议 |
|---|--------|------|------|
| R1 | **系统 SSH 私钥为单点故障** | 私钥泄露 = 所有服务器沦陷 | 1. 严格限制私钥访问权限 2. 考虑支持用户自带 Key 3. 定期轮换 |
| R2 | **部署过程中服务中断** | 发版期间服务不可用 | V1 可接受，但需在文档中明确声明。V2 考虑优雅重启（先启动新进程→健康检查通过→切换流量→停旧进程） |
| R3 | **并发部署冲突** | 同一服务同时被两个用户触发部署 | 使用 Redis 分布式锁（`SETNX`），同一服务同一时刻只允许一个部署任务 |
| R4 | **用户认证缺失** | PRD 声明 V1 无 RBAC 但多处引用 operator 字段 | 至少实现基础的用户登录/认证，`t_user` 表必须存在 |

### 🟡 中风险

| # | 风险项 | 影响 | 建议 |
|---|--------|------|------|
| R5 | **SSH 密码前端传输** | 中间人攻击可窃取密码 | 必须 HTTPS，密码字段在前端用 RSA 公钥加密后传输 |
| R6 | **WebSocket 连接泄漏** | 用户关闭页面后 `tail -f` 进程残留 | 建立 Session-进程映射，WebSocket 断开时 kill 远程进程 |
| R7 | **部署目录磁盘增长** | 版本产物不断累积，磁盘占满 | PRD 已提到"构建产物保留数=10"，需确保定时清理逻辑 |
| R8 | **健康检查误判** | 服务启动慢于健康检查等待时间，误判失败 | PRD 已设置默认 30 秒等待 + 5 次重试，合理。但需支持按服务自定义 |
| R9 | **Git 分支/Tag 列表拉取性能** | 大型仓库 `git ls-remote` 可能较慢 | 缓存结果（Redis 5 分钟 TTL），避免每次发版都拉取 |
| R10 | **钉钉 Webhook 安全** | Webhook URL 泄露可被恶意调用 | 使用"加签"模式而非"自定义关键词"，定期轮换签名密钥 |

### 🟢 低风险

| # | 风险项 | 影响 | 建议 |
|---|--------|------|------|
| R11 | **操作日志表增长** | 30 天自动清理，但高频率操作可能导致短期大量数据 | 建立 `create_time` 索引，定期清理任务使用分批删除 |
| R12 | **前端响应式布局** | 运维系统主要在 PC 使用，平板支持优先级低 | V1 可只做 PC 端适配，平板支持延至后续迭代 |
| R13 | **Monorepo 场景复杂度** | 多模块共用仓库，拉代码和编译逻辑复杂 | V1 先支持单仓库单模块，Monorepo 作为后续迭代 |

---

## 5. 改进建议

### 5.1 架构层面

1. **引入部署任务队列**：用户触发发版后，任务入队（Redis List 或 RabbitMQ），由 Worker 异步执行。避免 HTTP 请求超时问题。
2. **事件驱动架构**：部署完成后发布 `DeployEvent`，触发钉钉通知、操作日志记录等后续动作，解耦核心流程。
3. **配置外部化**：AES 加密密钥、钉钉 Webhook URL、SSH 私钥路径等敏感配置通过环境变量注入，不硬编码。

### 5.2 数据库层面

1. **新增 `t_user` 表**（必须）
2. **新增 `t_deploy_step` 表**（强烈建议）
3. **补充索引**（见 2.3 节）
4. **外键约束**：PRD 中 FK 标记了但未明确是否使用物理外键。建议：V1 不使用物理外键（MyBatis Plus 不擅长处理），通过应用层保证一致性
5. **软删除统一**：所有表都有 `deleted` 字段，建议统一使用 MyBatis Plus 的 `@TableLogic` 注解

### 5.3 安全层面

1. **HTTPS 强制**：生产环境必须启用 HTTPS
2. **API 限流**：登录接口增加限流（Redis + Token Bucket），防止暴力破解
3. **操作幂等性**：发版接口增加幂等控制（如 `deploy_token`），防止重复提交
4. **审计日志不可篡改**：`t_operation_log` 增加 checksum 字段或使用 append-only 表

### 5.4 部署层面

1. **OpsPilot 自身部署**：PRD 未提及 OpsPilot 系统自身的部署方案。建议提供 Docker 镜像或一键安装脚本
2. **数据库迁移工具**：建议使用 Flyway 或 Liquibase 管理数据库 DDL 变更
3. **健康检查端点**：OpsPilot 自身提供 `/actuator/health` 端点，支持外部监控

### 5.5 用户体验层面

1. **部署进度断线重连**：用户在发版过程中刷新页面，应能通过 WebSocket 重新连接并获取当前进度
2. **部署取消支持**：PRD 提到非生产环境可取消部署，但缺少取消后的清理逻辑说明（已编译的产物如何处理？）
3. **批量操作**：V1 不支持，但建议在 API 设计时预留批量部署接口

---

## 6. 评审检查清单

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 功能需求完整性 | ✅ | 覆盖核心运维场景 |
| 数据库表完整性 | ⚠️ | 缺少 `t_user`、`t_deploy_step` |
| 技术可行性 | ✅ | 技术栈选型合理 |
| 安全设计 | ⚠️ | SSH 私钥管理、AES 密钥管理需加强 |
| 非功能需求 | ✅ | 性能、安全、兼容性要求明确 |
| 验收标准 | ✅ | 可测试、可度量 |
| 术语一致性 | ✅ | 术语表清晰 |
| 版本规划 | ✅ | V1 范围明确，后续迭代有规划 |

---

## 7. 评审结论

### 结论：**条件通过** ✅（需修复以下问题后方可进入开发）

### 必须修复（Blocking）：

1. **新增 `t_user` 表**：没有用户表，操作人字段无数据源，登录认证无法实现
2. **明确 SSH 库选型**：JSch 或 SSHJ 必须二选一（推荐 SSHJ），不能模糊处理
3. **明确定时任务方案**：Spring Scheduled 或 Quartz 必须二选一（推荐 Quartz）

### 建议修复（Recommended）：

4. **新增 `t_deploy_step` 表**：部署步骤结构化存储，便于统计分析和进度展示
5. **完善索引设计**：在 DDL 阶段补充必要索引
6. **AES 加密密钥管理方案**：明确密钥注入方式（环境变量 / 配置文件 / KMS）
7. **WebSocket 连接生命周期管理**：明确断开后的远程进程清理方案

### 可选优化（Optional）：

8. 引入部署任务队列（异步化）
9. OpsPilot 自身部署方案文档
10. 数据库迁移工具选型（Flyway/Liquibase）

---

> **下一步**：修复上述 Blocking 问题后，可进入技术架构设计阶段（数据库 DDL 详细设计 + API 接口设计 + 模块划分）。
