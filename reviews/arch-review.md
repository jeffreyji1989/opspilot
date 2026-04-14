# OpsPilot 运维管理系统 — 架构评审报告

> **评审人**: 技术架构师（码哥 / Tech Architect）
> **评审日期**: 2026-04-14
> **评审对象**:
> - PRD: `projects/opspilot/prd/opspilot-prd.md` v1.0
> - UI 设计规范: `projects/opspilot/design/ui-spec.md` v1.0
> - 交互说明: `projects/opspilot/design/interaction-spec.md` v1.0
> - 高保真原型: `projects/opspilot/design/mockups/`（9 个 HTML 文件）
> - 数据库设计: `projects/opspilot/design/db-schema.md`
> - 之前的评审: `projects/opspilot/reviews/prd-review.md`
> - 现有后端代码: `projects/opspilot/src/opspilot-backend/src/`
> **已知技术决策**: Quartz（定时发版）、JDK 17、Spring Boot 3.2 + MyBatis Plus + Vue3 + Element Plus
> **评审结论**: ⚠️ **不通过**（需修复 Blocking 问题后方可进入下一阶段）

---

## 一、PRD 评审：核心功能技术可行性逐项检查

### 1.1 核心功能清单与可行性评估

| # | PRD 核心功能 | 技术可行性 | 状态 | 备注 |
|---|-------------|-----------|------|------|
| C1 | 项目管理（CRUD + 模块管理） | ✅ 可行 | 通过 | MyBatis Plus 标准 CRUD，前端 Element Plus 表格 |
| C2 | 服务器管理（添加、探测、分组） | ✅ 可行 | 通过 | SSHJ 已集成，探测脚本通过 SSH 远程执行 |
| C3 | Git 认证管理（SSH Key / HTTPS Token，加密存储） | ✅ 可行 | 通过 | AES-256-GCM + Base64 编码存储，db-schema 已设计 |
| C4 | 服务实例创建与目录结构初始化 | ✅ 可行 | 通过 | SSH 远程 mkdir 创建 `versions/build/logs/scripts` |
| C5 | 发版部署（7 步流程：拉代码→构建→部署→健康检查） | ⚠️ 部分可行 | 不通过 | 详见 ARCH-001 ~ ARCH-006 |
| C6 | 版本回退（软链切换 + 重启 + 健康检查） | ⚠️ 部分可行 | 不通过 | 详见 ARCH-007 |
| C7 | 实时日志（WebSocket + `tail -f`） | ⚠️ 部分可行 | 不通过 | 详见 ARCH-008 ~ ARCH-010 |
| C8 | 服务重启（停止→启动→健康检查） | ✅ 可行 | 通过 | 现有代码已实现 |
| C9 | 配置管理（环境变量、JVM 参数、启动脚本） | ✅ 可行 | 通过 | 存储于 `t_service_instance` 表，重启生效 |
| C10 | 定时发版（单次 / Cron，失败回退策略） | ✅ 可行 | 通过 | Quartz JDBC JobStore 已配置，db-schema 已设计 |
| C11 | 操作日志（全量审计） | ✅ 可行 | 通过 | `t_operation_log` 表已设计，AOP 可自动采集 |
| C12 | 钉钉通知（发版成功/失败/服务异常） | ✅ 可行 | 通过 | Webhook 调用简单，PRD 已提供通知模板 |
| C13 | 仪表盘（统计卡片、环境分布、服务健康） | ✅ 可行 | 通过 | 聚合查询即可，无技术难点 |
| C14 | 服务监控（进程、端口、响应时间、磁盘） | ⚠️ 部分可行 | 不通过 | 详见 ARCH-011 |
| C15 | 生产环境二次确认机制 | ✅ 可行 | 通过 | 前端交互层实现，后端需校验 env_type |

### 1.2 核心功能不通过项详细说明

#### ARCH-001 🔴 发版部署 — 编译构建在本地执行，与 PRD 设计不符

**问题**: PRD 3.6.3 发版步骤 ①② 说明"在服务器上拉取代码编译"，但实际代码 `DeployServiceImpl.pullCode()` 和 `buildProject()` 中 `sshManager.executeCommand(cmd, null, ...)` 传入 `server=null`，导致 `SshManager` 的 `executeCommand()` 走本地执行分支（`executeLocalCommand`）。

**影响**:
- 编译在 OpsPilot 本机执行，要求 OpsPilot 服务器安装 JDK/Maven/Node.js
- 与 PRD 描述的"在目标服务器上编译"方案完全不同
- 当多模块需要不同 JDK 版本时，本地环境无法同时满足

**修复建议**: 明确选择方案并统一实现：
- **方案 A（推荐）**：编译在目标服务器执行，需确保目标服务器有构建环境
- **方案 B**：编译在 OpsPilot 本机执行，需在 PRD 中明确此限制，并在服务器管理页展示 OpsPilot 构建能力

#### ARCH-002 🔴 发版部署 — 并发部署无分布式锁

**问题**: PRD 6.1 要求"最大并发部署数 = 3"，代码使用 `ThreadPoolTaskExecutor`（core=5, max=10），但**没有任何机制限制同一服务同时部署**。虽然 `deploy()` 方法中有 `processStatus` 检查，但检查与异步执行之间存在竞态窗口。

**修复建议**: 使用 Redis 分布式锁：
```java
String lockKey = "deploy:lock:" + instanceId;
Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.MINUTES);
if (!Boolean.TRUE.equals(locked)) {
    throw new BusinessException("该服务正在部署中，请稍后再试");
}
```

#### ARCH-003 🔴 发版部署 — 版本号生成规则与 PRD 不符

**问题**: PRD 8.2 版本号为 `{YYYYMMDD}_{序号}_{Tag名}`（如 `20260412_001_v1.0.0`），但代码中使用 `"v" + System.currentTimeMillis()`（如 `v1744617600000`）。

**修复建议**: 按 PRD 规则实现版本生成，查询当日最大序号并 +1。

#### ARCH-004 🟡 发版部署 — 健康检查仅单次执行，无重试

**问题**: PRD 要求"健康检查重试 N 次，间隔 N 秒"，代码中 `healthCheck()` 仅执行一次 curl 检查，无重试逻辑。

**修复建议**: 实现循环重试机制，使用 `t_deploy_schedule` 中配置的重试次数和间隔参数。

#### ARCH-005 🟡 发版部署 — 缺少构建超时控制

**问题**: PRD 非功能要求未明确构建超时，但 `buildProject()` 通过 `sshManager.executeCommand(cmd, null, 300)` 设置 300 秒超时。对于大型 Maven 项目可能不够。

**修复建议**: 在系统参数表中增加"构建超时"配置项（默认 30 分钟），支持按模块自定义。

#### ARCH-006 🟡 发版部署 — 回退逻辑使用 `ls -lt | head -2 | tail -1` 查找历史版本

**问题**: `DeployServiceImpl.executeRollback()` 中使用 shell 管道命令查找上一个版本目录，脆弱且不可靠（受文件名格式、ls 排序规则影响）。

**修复建议**: 从 `t_deploy_record` 表查询该实例最近的非回退成功记录，获取版本号后定位目录。

#### ARCH-007 🟡 版本回退 — 不支持指定目标版本回退

**问题**: PRD 3.6.3 要求"在发版记录中选择要回退的历史版本"，但 `rollback(Long instanceId, String operator)` 方法不接受目标版本参数，只能回退到"上一个"版本。

**修复建议**: 增加 `rollback(Long instanceId, Long targetVersionId, String operator)` 方法，支持指定目标版本。

#### ARCH-008 🟡 实时日志 — WebSocket 连接断开后 `tail -f` 进程未清理

**问题**: `SshManager.executeCommandAsync()` 中 Session 在 finally 中关闭，但 `tail -f` 是阻塞读取，WebSocket 断开时 Session 可能未正确关闭，导致远程 `tail -f` 进程残留。

**修复建议**: 维护 WebSocket Session ↔ SSH Session 映射表，`@OnClose` 时主动 kill 远程进程（`pkill -f 'tail -f'`）。

#### ARCH-009 🟡 实时日志 — 日志级别过滤未实现

**问题**: PRD 3.6.3 Tab 2 要求"ALL / ERROR / WARN / INFO / DEBUG"级别过滤，当前 WebSocket 代码无此功能。

**修复建议**: 前端发送级别参数，后端使用 `grep` 管道或正则过滤后推送。

#### ARCH-010 🟢 实时日志 — 日志行数限制未实现

**问题**: PRD 要求"默认展示最近 1000 行"，当前 `LogWebSocket` 无行数限制。

**修复建议**: WebSocket 连接时只推送最近 N 行历史，后续增量推送。

#### ARCH-011 🟡 服务监控 — 监控数据无定时采集机制

**问题**: PRD 3.6.3 Tab 4 要求"自动刷新（30s 间隔）"，`db-schema.md` 设计了 `t_server_monitor_log` 表，但代码中无定时采集任务。

**修复建议**: 使用 Quartz 或 Spring Scheduled 实现定时采集（30s 间隔），通过 SSH 执行 `top/free/df` 命令。

---

## 二、PRD 评审结论

### 结论：❌ 不通过

### 打回理由

1. **ARCH-001（🔴）**：核心发版流程的编译构建执行位置与 PRD 设计不符，属于**业务逻辑偏差**，必须明确方案后修改 PRD 或修改代码
2. **ARCH-002（🔴）**：并发部署缺少分布式锁保护，**生产环境下可能导致同一服务同时部署**，属于安全性问题
3. **ARCH-003（🔴）**：版本号生成规则完全偏离 PRD 定义，影响发版追溯性和回退功能

### 修改建议

1. 召开 PM + 全栈 + 架构师三方会议，明确 ARCH-001 的构建方案，并更新 PRD
2. 引入 Redis 分布式锁，修复 ARCH-002
3. 按 PRD 8.2 规则重新实现版本号生成
4. ARCH-004 ~ ARCH-011 可在进入测试阶段前修复

---

## 三、UI/UE 设计技术可行性评审

### 3.1 设计规范评审

| 评审项 | 评估 | 说明 |
|--------|------|------|
| 配色方案 | ✅ 通过 | Tailwind CSS 风格 Token，与 Element Plus 主题定制兼容性好 |
| 字体规范 | ✅ 通过 | Inter + 中文字体回退链，覆盖 Windows/macOS/Linux |
| 间距系统 | ✅ 通过 | 4px 基准，符合主流设计规范 |
| 圆角/阴影 | ✅ 通过 | 量化到具体像素值，前端可直接落地 |
| 组件规范 | ✅ 通过 | 基于 Element Plus 定制，无自研组件风险 |

### 3.2 交互说明评审

| 评审项 | 评估 | 说明 |
|--------|------|------|
| 二次确认机制 | ✅ 通过 | 生产环境操作覆盖完整，对话框设计清晰 |
| 操作进度展示 | ✅ 通过 | 7 步部署进度 + 实时日志，与 PRD 一致 |
| 错误处理 | ✅ 通过 | 覆盖所有异常场景，Toast 规范量化 |
| 页面状态覆盖 | ✅ 通过 | 9 个页面 × 5 种状态 = 45 个状态组合，覆盖完整 |
| 响应式适配 | ✅ 通过 | 1920px / 1440px / 768px 三种断点明确 |

### 3.3 高保真原型评审

| 原型文件 | 评估 | 说明 |
|----------|------|------|
| login.html | ✅ 通过 | 布局完整，表单验证交互清晰 |
| dashboard.html | ✅ 通过 | 统计卡片 + 环境分布 + 服务健康 + 最近操作 |
| project-manage.html | ✅ 通过 | 表格 + 搜索 + 模块管理弹窗 |
| server-manage.html | ✅ 通过 | 环境分组 + 卡片展示 + 添加服务器流程 |
| service-instance.html | ✅ 通过 | 服务列表 + 详情页 + 5 个 Tab 页签 |
| deploy-wizard.html | ✅ 通过 | 4 步向导完整，进度条 + 实时日志 |
| git-credential.html | ✅ 通过 | 列表 + 添加/编辑表单 |
| operation-log.html | ✅ 通过 | 多条件筛选 + 表格 + 导出 |
| schedule-task.html | ✅ 通过 | 任务列表 + Cron 表达式帮助 |

### 3.4 技术可行性风险

| # | 风险项 | 等级 | 说明 | 应对策略 |
|---|--------|------|------|----------|
| UI-001 | 实时日志 WebSocket 前端断线重连 | 🟡 中 | PRD 4.2.2 和交互说明均未明确断线重连机制 | 使用 WebSocket + 心跳机制，断线自动重连并获取最近日志 |
| UI-002 | 发版进度弹窗断线重连 | 🟡 中 | 用户刷新页面后如何恢复进度展示 | 使用 `recordId` 作为恢复锚点，通过轮询 `getProgress` 恢复 |
| UI-003 | 768px 平板端响应式降级 | 🟢 低 | 运维系统主要在 PC 使用，平板端使用频率低 | 交互说明已有完整适配方案，按方案实现即可 |

### 3.5 UI/UE 评审结论

### 结论：✅ 通过

**理由**: UI 设计规范量化完整（Token 体系），交互说明覆盖所有状态组合，高保真原型与 PRD 功能一一对应。技术实现无重大风险，仅需补充 WebSocket 断线重连方案。

---

## 四、数据库设计评审

### 4.1 表结构评审

| 表名 | 评价 | 说明 |
|------|------|------|
| `t_user` | ✅ 通过 | 字段完整，BCrypt 密码，唯一索引覆盖登录场景 |
| `t_project` | ✅ 通过 | 唯一约束 `uk_project_code`，索引覆盖查询场景 |
| `t_module` | ✅ 通过 | 联合唯一约束 `(project_id, module_name)`，索引合理 |
| `t_server` | ✅ 通过 | 环境类型 TINYINT，SSH 状态追踪，自动探测字段齐全 |
| `t_git_credential` | ✅ 通过 | 加密数据 TEXT 存储，指纹/过期时间字段到位 |
| `t_service_instance` | ✅ 通过 | 运行时类型/版本/JVM 参数字段完整，进程状态可追踪 |
| `t_deploy_record` | ✅ 通过 | 发版流水号唯一约束，回退来源关联字段 |
| `t_deploy_step` | ✅ 通过 | 步骤明细，关联发版记录，日志输出 TEXT 字段 |
| `t_deploy_schedule` | ✅ 通过 | Cron 表达式、时区、回退策略、钉钉通知字段完整 |
| `t_operation_log` | ✅ 通过 | 无 deleted 字段（日志表正确），HTTP 请求信息完整 |
| `t_alert_rule` | ✅ 通过 | 监控指标、阈值、冷却时间、告警模板字段完整 |
| `t_server_monitor_log` | ✅ 通过 | 复合索引 `(server_id, create_time)` 适合趋势查询 |

### 4.2 索引设计评审

| 表 | 索引 | 评价 | 说明 |
|----|------|------|------|
| t_deploy_record | `uk_deploy_no` | ✅ | 唯一流水号，防重复发版 |
| t_deploy_record | `idx_instance_id` | ✅ | 按实例查发版历史（高频） |
| t_deploy_record | `idx_create_time` | ✅ | 按时间范围查询（高频） |
| t_deploy_record | `idx_status` | ✅ | 按状态筛选 |
| t_deploy_record | `idx_schedule_id` | ✅ | 关联定时任务查询 |
| t_deploy_record | `idx_module_id` | ✅ | 按模块查发版历史 |
| t_deploy_record | `idx_operator_id` | ✅ | 按操作人查询 |
| t_deploy_record | `idx_version` | ✅ | 按版本号查询 |
| t_service_instance | `uk_instance_name` | ✅ | 唯一实例名 |
| t_service_instance | `idx_module_id` | ✅ | 按模块查实例 |
| t_service_instance | `idx_server_id` | ✅ | 按服务器查实例 |
| t_server_monitor_log | `idx_server_time` | ✅ | 复合索引，趋势查询最优 |
| t_operation_log | `idx_create_time` | ✅ | 时间范围查询 |
| t_operation_log | `idx_target` | ✅ | 复合索引 `(target_type, target_id)` |

**⚠️ 遗漏索引**:

| 表 | 建议索引 | 理由 |
|----|----------|------|
| t_server_monitor_log | `idx_create_time` 单独索引 | `t_server_monitor_log` 已有复合索引 `(server_id, create_time)`，但若存在按时间范围查询所有服务器的场景，需要单独 `create_time` 索引 |

### 4.3 字段类型选择评审

| 字段 | 类型 | 评价 | 说明 |
|------|------|------|------|
| `env_type` (TINYINT) | ✅ | 枚举值固定（0-3），TINYINT 比 VARCHAR 更高效 |
| `status` (TINYINT) | ✅ | 状态机场景，数值型比字符串更节省存储 |
| `encrypted_data` (TEXT) | ✅ | 加密后数据长度不确定，TEXT 合适 |
| `ip_address` (VARCHAR(45)) | ✅ | IPv6 最长 45 字符，设计正确 |
| `jvm_options` (VARCHAR(2048)) | ✅ | JVM 参数通常不超过 2048 字符 |
| `cron_expression` (VARCHAR(64)) | ✅ | 标准 Cron 表达式不超过 64 字符 |
| `duration_seconds` (INT) | ✅ | 秒级精度足够，INT 范围足够 |
| `cpu_cores` / `memory_mb` (INT) | ✅ | 硬件指标，INT 范围足够 |

### 4.4 外键约束评审

**结论**: ❌ 不使用物理外键 ✅

**理由**:
1. MyBatis Plus 不擅长处理物理外键（级联操作需手动处理）
2. 运维系统需要高可用，物理外键在分库分表/迁移时增加复杂度
3. 通过应用层保证引用完整性（Service 层删除前校验）
4. db-schema.md 中未使用 `CONSTRAINT ... FOREIGN KEY`，符合最佳实践

**建议**: 在 Service 层增加级联删除校验，例如删除 Server 前检查是否有 ServiceInstance 引用。

### 4.5 数据量预估

| 表 | 预估数据量 | 说明 |
|----|-----------|------|
| t_user | < 100 条 | 小团队 5-20 人 |
| t_project | < 50 条 | 项目数量有限 |
| t_module | < 200 条 | 每个项目 2-10 个模块 |
| t_server | < 100 条 | 5-20 台服务器 |
| t_service_instance | < 200 条 | 每个服务器 1-5 个服务 |
| t_deploy_record | 10K-100K 条/年 | 每日发版 10-50 次 × 365 天 |
| t_deploy_step | 70K-700K 条/年 | 每条发版记录 7 步 |
| t_deploy_schedule | < 50 条 | 定时任务数量有限 |
| t_operation_log | 50K-500K 条/年 | 所有操作的审计记录 |
| t_server_monitor_log | 5M-50M 条/年 | 每 30s 采集 × 服务器数量 × 365 |

**分库分表建议**: V1 数据量在 MySQL 单表能力范围内，**无需分库分表**。若 `t_server_monitor_log` 超过 1000 万条，建议按月分区或定期归档清理。

### 4.6 数据库设计评审结论

### 结论：✅ 通过（需补充 ARCH-012 索引）

**理由**: 12 张表设计完整，覆盖 PRD 所有功能需求。字段类型选择合理，索引覆盖主要查询场景。物理外键不使用符合 MyBatis Plus 最佳实践。

---

## 五、现有代码质量评审

### 5.1 架构分层

| 层级 | 评价 | 说明 |
|------|------|------|
| Controller → Service → Mapper | ✅ | 分层清晰，无跨层调用 |
| DTO 与 Entity 分离 | ⚠️ | `DeployRequest` 存在但使用不完整，部分接口直接用 `Map<String, Object>` |
| 统一响应格式 | ✅ | `Result<T>` 统一返回 `{code, message, data}` |

### 5.2 命名规范评审（阿里巴巴 Java 开发手册）

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 类名 PascalCase | ✅ | 如 `DeployServiceImpl`, `SshManager` |
| 方法 camelCase | ✅ | 如 `executeDeploy`, `healthCheck` |
| 常量 | ❌ | `LOCAL_BUILD_DIR` 为魔法字符串，但其他如状态码使用枚举 ✅ |
| 无魔法值 | ⚠️ | `DeployServiceImpl` 中多处使用硬编码数字（如 `step.setStatus(0)`） |

**CODE-001 🔴**: `Module` 实体类缺少 Lombok `@Data` 和 MyBatis Plus `@TableName` 注解，与其他实体类风格不一致。且字段类型为 `Date` 而非 `LocalDateTime`，与全局约定不符。

**CODE-002 🟡**: `DeployServiceImpl` 中 `executeStep()` 方法使用硬编码 `0/1/2` 表示步骤状态，应使用枚举或常量。

### 5.3 异常处理评审

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 无空 catch 块 | ✅ | 所有 catch 都有日志输出 |
| 无 e.printStackTrace() | ✅ | 统一使用 SLF4J |
| 异常不吞掉 | ✅ | 异常向上抛出或记录后返回失败 |
| 自定义异常 | ✅ | `BusinessException` 存在 |

### 5.4 日志评审

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 使用 SLF4J | ✅ | `LoggerFactory.getLogger()` |
| 包含上下文 | ✅ | 日志包含 recordId, moduleId, instanceId |
| 核心业务日志 | ✅ | 发版开始/成功/失败/回退均有日志 |
| 敏感信息泄露 | ⚠️ | `SshManager` 日志中打印了 `server.getHostname()` 和 `server.getSshUsername()`，生产环境可能泄露敏感信息 |

### 5.5 SQL 评审

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 无 SELECT * | ⚠️ | MyBatis Plus 默认查询所有列，需通过 `@TableField(select = false)` 控制 |
| 参数化查询 | ✅ | MyBatis Plus 自动参数化 |
| 索引使用 | ✅ | Mapper 查询走索引 |

### 5.6 并发评审

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 线程池手动创建 | ✅ | `AsyncConfig` 手动创建 `ThreadPoolTaskExecutor` |
| 共享变量线程安全 | ⚠️ | `SshManager` 非线程安全组件，`executeCommand()` 每次创建新 SSHClient ✅ |
| 并发部署锁 | ❌ | 缺少分布式锁，同一服务可能同时部署 |

### 5.7 安全评审

| 检查项 | 结果 | 说明 |
|--------|------|------|
| BCrypt 密码 | ✅ | init.sql 中使用 BCrypt 哈希 |
| AES 加密密钥 | 🔴 | `application.yml` 中硬编码 `DefaultAesKey16Ch` |
| JWT 密钥 | 🔴 | `application.yml` 中硬编码 `OpsPilotSecretKey2024ChangeMeInProduction` |
| SSH 主机密钥验证 | 🔴 | `SshManager.connect()` 使用 `PromiscuousVerifier()`（跳过主机验证），存在 MITM 风险 |
| SQL 注入 | ✅ | MyBatis Plus 参数化 |

**SEC-001 🔴**: AES 加密密钥和 JWT 密钥硬编码在 `application.yml` 中，必须通过环境变量注入。

**SEC-002 🔴**: SSH 主机密钥验证跳过（`PromiscuousVerifier`），生产环境存在中间人攻击风险。

### 5.8 CORS 配置评审

**CODE-003 🟡**: `WebMvcConfig.addCorsMappings()` 中 `allowedOriginPatterns("*")` 允许所有来源，生产环境应限制为前端域名。

### 5.9 代码评审结论

### 结论：❌ 不通过

### 打回理由

1. **CODE-001（🔴）**：`Module` 实体缺少标准注解，与全局约定不一致
2. **SEC-001（🔴）**：敏感密钥硬编码在配置文件中
3. **SEC-002（🔴）**：SSH 主机密钥验证被跳过，安全风险

---

## 六、风险评估汇总

| # | 风险项 | 等级 | 影响 | 应对策略 |
|---|--------|------|------|----------|
| R1 | 系统 SSH 私钥为单点故障 | 🔴 高 | 私钥泄露 = 所有服务器沦陷 | 限制私钥权限 600，支持用户自带 Key，定期轮换 |
| R2 | 部署过程中服务中断 | 🟡 中 | 发版期间服务不可用 | V1 可接受，V2 引入优雅重启 |
| R3 | 并发部署冲突 | 🔴 高 | 同一服务同时被两个用户触发部署 | Redis 分布式锁（SETNX），修复 ARCH-002 |
| R4 | 编译构建执行位置偏差 | 🔴 高 | 与 PRD 设计不符，影响部署方案 | 明确方案（ARCH-001），更新 PRD 或代码 |
| R5 | SSH 密码传输安全 | 🟡 中 | 中间人攻击可窃取密码 | 必须 HTTPS，前端 RSA 加密传输 |
| R6 | WebSocket 连接泄漏 | 🟡 中 | `tail -f` 进程残留 | Session-进程映射表，断开时 kill（ARCH-008） |
| R7 | 部署目录磁盘增长 | 🟡 中 | 版本产物不断累积 | PRD 已设计"构建产物保留数=10"，确保清理逻辑 |
| R8 | 健康检查误判 | 🟢 低 | 服务启动慢导致误判失败 | 实现重试机制（ARCH-004） |
| R9 | 密钥硬编码 | 🔴 高 | AES/JWT 密钥泄露 | 环境变量注入（SEC-001） |
| R10 | SSH 主机验证跳过 | 🔴 高 | MITM 攻击风险 | 使用已知主机密钥验证（SEC-002） |
| R11 | CORS 通配符 | 🟡 中 | 跨站请求伪造风险 | 生产环境限制为前端域名（CODE-003） |

---

## 七、与之前评审（prd-review.md）的变更对比

| 评审项 | prd-review.md 结论 | 本次评审 | 变化 |
|--------|-------------------|---------|------|
| SSH 库选型 | ⚠️ 需明确（推荐 SSHJ） | ✅ 已选 SSHJ 0.38.0 | 已解决 |
| 定时任务方案 | ⚠️ 需明确（推荐 Quartz） | ✅ 已配置 Quartz JDBC | 已解决 |
| 用户表 `t_user` | ❌ 缺失 | ✅ 已设计并创建 | 已解决 |
| 部署步骤表 `t_deploy_step` | ⚠️ 建议新增 | ✅ 已设计并创建 | 已解决 |
| 数据库索引 | ⚠️ 建议补充 | ✅ 大部分已补充 | 基本解决 |
| 并发部署锁 | ⚠️ 建议使用 Redis 锁 | ❌ 代码未实现 | 未解决（ARCH-002） |
| AES 加密密钥管理 | ⚠️ 需明确注入方式 | ❌ 硬编码在 yml | 未解决（SEC-001） |
| WebSocket 连接管理 | ⚠️ 需明确生命周期 | ⚠️ 有异步方法但缺清理 | 部分解决（ARCH-008） |

---

## 八、总体评审结论

### 最终结论：❌ **不通过**

### 不通过原因汇总（Blocking）

| 编号 | 严重度 | 问题描述 | 修复优先级 |
|------|--------|----------|-----------|
| ARCH-001 | 🔴 | 发版编译构建执行位置与 PRD 设计不符 | P0 - 必须先确认方案 |
| ARCH-002 | 🔴 | 并发部署无分布式锁 | P0 - 防止生产事故 |
| ARCH-003 | 🔴 | 版本号生成规则偏离 PRD | P0 - 影响发版追溯 |
| SEC-001 | 🔴 | AES/JWT 密钥硬编码 | P0 - 安全红线 |
| SEC-002 | 🔴 | SSH 主机验证跳过 | P0 - 安全红线 |
| CODE-001 | 🔴 | Module 实体不符合全局约定 | P1 - 代码一致性 |

### 建议修复项（Recommended）

| 编号 | 严重度 | 问题描述 | 修复优先级 |
|------|--------|----------|-----------|
| ARCH-004 | 🟡 | 健康检查无重试机制 | P1 |
| ARCH-005 | 🟡 | 缺少构建超时配置 | P1 |
| ARCH-006 | 🟡 | 回退逻辑使用 shell 管道 | P1 |
| ARCH-007 | 🟡 | 不支持指定目标版本回退 | P1 |
| ARCH-008 | 🟡 | WebSocket 断线后进程未清理 | P1 |
| ARCH-009 | 🟡 | 日志级别过滤未实现 | P2 |
| ARCH-010 | 🟢 | 日志行数限制未实现 | P2 |
| ARCH-011 | 🟡 | 监控数据无定时采集 | P1 |
| ARCH-012 | 🟢 | `t_server_monitor_log` 缺少单独 create_time 索引 | P2 |
| CODE-002 | 🟡 | 硬编码数字替代枚举 | P2 |
| CODE-003 | 🟡 | CORS 通配符 | P1 - 生产前修复 |
| UI-001 | 🟡 | WebSocket 断线重连方案缺失 | P2 |

### 下一步行动

1. **全栈工程师**：修复 ARCH-001 ~ ARCH-003（发版流程相关），CODE-001 ~ CODE-003（代码规范）
2. **全栈工程师**：修复 SEC-001 ~ SEC-002（安全红线）
3. **产品经理**：确认 ARCH-001 的构建方案，如需变更则更新 PRD
4. **架构师**：修复完成后进行二次评审

---

> **评审人签名**: 码哥（Tech Architect）
> **评审日期**: 2026-04-14
> **下次评审**: 修复 Blocking 问题后预约二次评审
