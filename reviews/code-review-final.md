# OpsPilot 运维管理系统 — 最终代码评审报告

> **评审人**: 技术架构师（码哥 / Tech Architect）
> **评审日期**: 2026-04-15
> **评审对象**: test 分支全部代码（全栈 A 基础数据层 + 全栈 B v2 核心运维层合并）
> **参考文档**:
> - PRD: `projects/opspilot/prd/opspilot-prd.md` v1.0
> - UI 规范: `projects/opspilot/design/ui-spec.md` v1.0
> - 交互说明: `projects/opspilot/design/interaction-spec.md` v1.0
> - 数据库设计: `projects/opspilot/design/db-schema.md`
> - 首次架构评审: `projects/opspilot/reviews/arch-review.md`
> - 二次架构评审: `projects/opspilot/reviews/arch-review-fix.md`
> - GitHub 工作流: `projects/opspilot/GITHUB-WORKFLOW.md`

---

## 一、PRD 核心功能逐项检查

| # | PRD 核心功能 | 实现状态 | 状态 | 说明 |
|---|-------------|---------|------|------|
| C1 | 🏠 仪表盘（统计卡片、环境分布、服务健康、最近操作） | ✅ 完整实现 | 通过 | `DashboardController` + `Dashboard.vue`，4 项统计卡片、环境分布、服务健康网格、最近 10 条操作、快捷入口均已实现 |
| C2 | 📁 项目管理（CRUD + 模块管理） | ✅ 实现 | 通过 | `ProjectController` + `Projects.vue`，分步创建向导，模块管理已实现 |
| C3 | 🖥️ 服务器管理（添加、探测、分组） | ✅ 实现 | 通过 | `ServerController` + `Servers.vue`，环境分组展示，SSH 信任配置，环境探测均已实现 |
| C4 | 🔑 Git 认证管理（SSH Key / Token，加密存储） | ✅ 实现 | 通过 | `GitCredentialController` + `GitCredentials.vue`，`AesGcmUtil` 加密解密 |
| C5 | 🔧 服务实例 CRUD + 目录初始化 | ✅ 实现 | 通过 | `ServiceInstanceServiceImpl.createInstanceWithDirSetup()`，SSH 创建 `versions/current/releases/build/logs/scripts` 目录 |
| C6 | 🚀 发版部署（7 步流程） | ✅ 实现 | 通过 | `DeployServiceImpl.executeDeploy()` 完整 7 步：拉取代码→编译构建→打包产物→上传→切换版本→重启→健康检查 |
| C7 | ⏪ 版本回退 | ⚠️ 部分实现 | 不通过 | 详见 CR-001：两条回退路径不一致，`DeployService.rollback()` 不支持指定目标版本 |
| C8 | 📋 实时日志（WebSocket + tail -f） | ✅ 实现 | 通过 | `LogWebSocket` + `tail -n 500 -f`，支持级别过滤、关键字搜索、行数限制、自动滚动 |
| C9 | ⚙️ 配置管理（JVM 参数、启动命令编辑） | ✅ 实现 | 通过 | `ServiceDetail.vue` 配置管理 Tab，JVM 参数和启动命令均可编辑保存 |
| C10 | 📊 服务监控（CPU/内存/磁盘/进程） | ✅ 实现 | 通过 | `ServiceInstanceServiceImpl.getMonitor()`，SSH 执行 top/free/df/ps 命令 |
| C11 | ⏰ 定时发版（Quartz + Cron） | ✅ 实现 | 通过 | `DeployScheduleServiceImpl` + `DeployJob`，Quartz JDBC JobStore，支持暂停/恢复/立即触发 |
| C12 | 📜 操作日志（全量审计） | ✅ 实现 | 通过 | `OperationLogController` + `OperationLogs.vue`，AOP 注解 + 手动记录 |
| C13 | 🔔 钉钉通知 | ⚠️ 部分实现 | 不通过 | 详见 CR-002：`DingTalkService` 类存在但未被调用集成 |
| C14 | 生产环境二次确认机制 | ⚠️ 部分实现 | 不通过 | 详见 CR-003：前端 `EnvConfirmDialog` 组件存在但未在生产环境操作中强制使用 |

---

## 二、后端代码评审（阿里巴巴 Java 开发手册）

### 2.1 🔴 严重问题（Blocking）

#### CR-001 🔴 版本回退 — 两条回退路径不一致，`DeployService.rollback()` 不支持指定目标版本

**位置**: `DeployServiceImpl.rollback()` vs `ServiceInstanceServiceImpl.rollback()`

**问题**:
- `ServiceInstanceServiceImpl.rollback(Long instanceId, String targetVersion)` 接受目标版本参数，实现正确（软链切换 + 重启）
- `DeployService.rollback(Long instanceId, String operator)` **不接受目标版本参数**，回退逻辑使用 `ls -lt | grep '^d' | head -2 | tail -1 | awk '{print $NF}'` 查找上一版本目录
- 前端 `ServiceDetail.vue` 调用的是 `/services/{id}/rollback`（即 `ServiceInstanceServiceImpl` 路径），但 `/deploy/rollback/{instanceId}` 路径仍指向旧版 `DeployService.rollback()`
- `DeployService.rollback()` 的 shell 管道查找方式脆弱且不可靠（ARCH-006 已标注但未修复）

**违反规范**: 阿里手册 — "同一业务逻辑不应存在多套不一致的实现"

**修复建议**: 统一回退逻辑到 `ServiceInstanceServiceImpl.rollback()` 版本，删除或重构 `DeployService.rollback()`，支持指定目标版本回退。

---

#### CR-002 🔴 Shell 命令注入风险 — 多处命令拼接未经安全转义

**位置**: `DeployServiceImpl`、`ServiceInstanceServiceImpl`、`SshManager`

**问题**:
- `DeployServiceImpl.switchVersion()`: `String.format("mkdir -p %s && cp %s/* %s/", versionDir, uploadsDir, versionDir)` — `deployPath` 包含特殊字符时可注入命令
- `DeployServiceImpl.pullCode()`: `String.format("cd %s && git pull origin %s", projectDir, branch)` — `branch` 参数可注入
- `DeployServiceImpl.buildProject()`: `String.format("cd %s && %s", projectDir, buildCmd)` — `buildCmd` 完全可控
- `SshManager.setupSshTrust()`: `"echo '" + publicKey + "' >> ~" + user + "/.ssh/authorized_keys"` — `publicKey` 可注入
- `ServiceInstanceServiceImpl.createInstanceWithDirSetup()`: `String.format("mkdir -p %s/versions/current/releases ...", base)` — `deployPath` 可注入

**违反规范**: 阿里手册 — "所有外部输入必须经过合法性校验和转义"

**修复建议**:
1. 对所有路径参数进行合法性校验（正则匹配 `^/[a-zA-Z0-9/_.-]+$`）
2. 使用 SSHJ 的 `Session.ExecChannel` 配合参数化执行，或至少对拼接参数做 shell 转义（单引号包裹）

---

#### CR-003 🔴 CORS 通配符 + 允许凭证 = 安全漏洞

**位置**: `WebMvcConfig.addCorsMappings()`

**问题**:
```java
registry.addMapping("/**")
    .allowedOriginPatterns("*")  // 允许所有来源
    .allowCredentials(true)       // 允许携带 Cookie/凭证
```
`allowCredentials(true)` 与 `allowedOriginPatterns("*")` 同时使用是**严重安全配置错误**。虽然现代浏览器会拒绝此类组合（CORS 规范要求），但这表明配置者不了解 CORS 安全机制。生产环境必须限制为前端域名。

**违反规范**: OWASP A01:2021 — Broken Access Control

**修复建议**: 生产环境配置为前端域名白名单，如 `allowedOriginPatterns("http://localhost:3000", "https://opspilot.example.com")`。

---

#### CR-004 🔴 WebSocket 无身份验证

**位置**: `WebMvcConfig.addInterceptors()` + `LogWebSocket`

**问题**:
```java
.excludePathPatterns("/api/ws/**")  // WebSocket 排除 JWT 验证
```
`LogWebSocket` 的 `@OnOpen` 中**没有任何身份验证**。任何知道 `instanceId` 的客户端都可以建立 WebSocket 连接并实时查看服务器日志（包含敏感信息如错误堆栈、路径信息等）。

**违反规范**: 阿里手册 — "所有对外接口必须进行身份验证"

**修复建议**: WebSocket 连接建立时验证 JWT Token（通过查询参数或子协议传递），或在 WebSocket 建立前通过 HTTP 接口获取一次性连接凭证。

---

#### CR-005 🔴 数据库密码硬编码

**位置**: `application.yml`

**问题**:
```yaml
spring:
  datasource:
    username: root
    password: root
```
MySQL root 密码 `root/root` 硬编码在配置文件中，与 SEC-001（JWT/AES 密钥）修复风格不一致。

**修复建议**: 通过环境变量注入：`${DB_PASSWORD:}`，并在启动脚本中要求配置。

---

### 2.2 🟡 中等严重问题（Recommended）

#### CR-006 🟡 前端错误处理缺失 — 所有 `catch (e) {}` 空捕获

**位置**: `ServiceDetail.vue`、`Services.vue`、`Dashboard.vue` 等

**问题**: 前端几乎所有 API 调用的 `catch` 块都是空的：
```javascript
try {
  await api.post(`/services/${instanceId.value}/restart`)
  ElMessage.success('重启成功')
} catch (e) {}  // 吞掉所有错误
```
用户操作失败时没有任何反馈（Axios 拦截器虽会弹错误 Toast，但页面状态不会回滚，用户不知道操作是否成功）。

**修复建议**: 至少在 catch 中添加状态回滚或用户提示。

---

#### CR-007 🟡 N+1 查询问题 — 前端模块加载

**位置**: `Services.vue` → `fetchModules()`

**问题**:
```javascript
for (const p of projects.value) {
  const mr = await api.get(`/projects/${p.id}/modules`)  // 每个项目一次请求
  modules.value.push(...mr.data)
}
```
项目数量 × 1 次额外 API 调用，应使用批量接口。

**修复建议**: 后端提供 `/modules?projectId=1,2,3` 批量查询接口，或前端在获取项目列表时同时加载所有模块。

---

#### CR-008 🟡 `DeployController.executeDeploy` 使用 `Map<String, Object>` 接收参数

**位置**: `DeployController.executeDeploy()`

**问题**:
```java
public Result<Long> executeDeploy(@RequestBody Map<String, Object> request) {
    Long moduleId = Long.valueOf(request.get("moduleId").toString());  // 可能 NPE
```
- 未使用 `@Valid` + `DeployRequest` DTO 进行参数校验
- `request.get("moduleId")` 可能返回 `null` 导致 NPE
- `gitBranch` 和 `gitCommit` 接收了但未使用（TODO 注释残留）

**违反规范**: 阿里手册 — "Controller 层入参应使用明确的 DTO，禁止使用 Map 接收业务参数"

**修复建议**: 使用 `@Valid @RequestBody DeployRequest request` 接收，利用 Bean Validation 自动校验。

---

#### CR-009 🟡 发版进度轮询效率低

**位置**: `ServiceDetail.vue` → `startDeployPolling()`

**问题**:
```javascript
deployPollTimer = setInterval(async () => {
    // 每次轮询调用 2 次 API
    const deploysRes = await api.get('/deploy/history', ...)
    const progressRes = await api.get(`/deploy/progress/${latest.id}`)
}, 2000)  // 2 秒间隔
```
每 2 秒调用 2 个 API，整个部署过程（通常 1-5 分钟）产生 60-300 次 API 请求。

**修复建议**: 使用 Server-Sent Events (SSE) 或 WebSocket 推送部署进度，或增加轮询间隔（5-10 秒）。

---

#### CR-010 🟡 `LogWebSocket` 使用 `new Thread()` 创建线程

**位置**: `LogWebSocket.onOpen()`

**问题**:
```java
new Thread(() -> startLogStreaming(key, instanceId, session, level, finalMaxLines, keyword)).start();
```
直接使用 `new Thread()` 创建线程，未使用线程池管理。大量并发连接会导致线程泄漏。

**违反规范**: 阿里手册 — "线程资源必须通过线程池提供，不允许在应用中自行显式创建线程"

**修复建议**: 注入 `ThreadPoolTaskExecutor`，使用 `executor.execute(() -> startLogStreaming(...))`。

---

#### CR-011 🟡 `GlobalExceptionHandler` 中 DuplicateKeyException 消息硬编码

**位置**: `GlobalExceptionHandler.handleDuplicateKeyException()`

**问题**:
```java
return Result.error(10002, "项目编码已存在");  // 硬编码中文消息
```
该异常处理器是全局的，但错误消息仅针对项目编码，其他表的唯一约束冲突也会显示"项目编码已存在"。

**修复建议**: 根据异常中的 SQL 信息动态生成消息，或使用通用消息"数据已存在，请勿重复提交"。

---

#### CR-012 🟡 `SshManager` 每次命令创建新 SSH 连接

**位置**: `SshManager.executeCommand(String, Server, int)`

**问题**:
```java
public String executeCommand(String command, Server server, int timeoutSeconds) {
    SSHClient ssh = connect(server);  // 每次命令新建连接
    try {
        return executeCommand(ssh, command, timeoutSeconds);
    } finally {
        ssh.close();  // 立即关闭
    }
}
```
`DeployServiceImpl.executeDeploy()` 中执行 7+ 步操作，每步都建立/关闭 SSH 连接（约 10-20 次连接/次部署）。SSH 握手耗时约 100-500ms，总计增加 1-10 秒额外开销。

**修复建议**: 在 `executeDeploy()` 方法级别复用 SSH 连接，整个部署流程使用同一个 SSHClient 实例。

---

#### CR-013 🟡 `DeployServiceImpl.healthCheck()` 中 `Thread.sleep()` 阻塞部署线程

**位置**: `DeployServiceImpl.healthCheck()` + `restartService()`

**问题**: `Thread.sleep(5000)` 在重试循环中阻塞线程。3 次重试 × 5 秒 = 至少 15 秒线程占用。线程池仅有 10 个最大线程，大量健康检查可能耗尽线程池。

**修复建议**: 使用 `ScheduledExecutorService` 实现异步重试，或使用非阻塞方式。

---

#### CR-014 🟡 `DeployScheduleServiceImpl` 缺少 Cron 表达式校验

**位置**: `DeployScheduleServiceImpl.addQuartzJob()`

**问题**: `schedule.getCronExpression()` 直接传入 Quartz，未校验格式。无效 Cron 表达式会导致 `SchedulerException`，虽然被捕获，但用户得到的错误信息不够友好。

**修复建议**: 使用 `CronExpression.isValidExpression()` 预校验。

---

#### CR-015 🟡 `OperationLog` 表缺少 `env_type` 字段

**位置**: `t_operation_log` 表 + `OperationLog` 实体

**问题**: db-schema 设计中 `t_operation_log` 没有 `env_type` 字段，但 PRD 5.1.7 明确要求该字段。前端 `OperationLogs.vue` 也无法按环境筛选操作日志。

**修复建议**: 为 `t_operation_log` 添加 `env_type` 字段，并在操作日志记录时自动填充。

---

### 2.3 🟢 轻微问题（Minor）

| 编号 | 严重度 | 位置 | 问题描述 | 建议 |
|------|--------|------|---------|------|
| CR-016 | 🟢 | `DeployStatusEnum` | 枚举值跳过了 4（0,1,2,3,5,6），缺少状态 4 定义 | 补充状态 4 或删除注释中的状态 4 引用 |
| CR-017 | 🟢 | `DeployServiceImpl` | `generateVersion()` JavaDoc 示例 `20260414_001_v1.0.0` 与实际 `20260414_001_moduleName` 不一致（FIX-004 遗留） | 修正 JavaDoc 示例 |
| CR-018 | 🟢 | `DeployServiceImpl` | `packageArtifact()` 中 `find` 命令使用 `-o` 无括号分组，优先级问题：`find dir -name '*.jar' -o -name '*.war'` 可能误匹配 | 改为 `find dir \( -name '*.jar' -o -name '*.war' \)` |
| CR-019 | 🟢 | `ServiceInstanceServiceImpl` | `restartService()` 中使用 `Thread.sleep(2000)` 同步等待 | 可接受（短时间），但建议用更优雅的方式 |
| CR-020 | 🟢 | `DeployController` | `executeDeploy` 方法中 TODO 注释残留：`// TODO: update via mapper` | 完成实现或删除 TODO |
| CR-021 | 🟢 | `Servers.vue` | 前端 `fetchData()` 中 `statusFilter` 默认值为 `null`，但后端期望整数 | 确保类型一致 |
| CR-022 | 🟢 | `ServiceDetail.vue` | 重启操作在 `Services.vue` 中使用 `el-popconfirm`，在 `ServiceDetail.vue` 中使用 `EnvConfirmDialog`，交互不一致 | 统一使用 `EnvConfirmDialog` |
| CR-023 | 🟢 | `DeployServiceImpl` | `executeStep()` 中步骤状态使用硬编码 `0/1/2`，应使用枚举 | 引入 `DeployStepStatusEnum` |
| CR-024 | 🟢 | 全局 | 缺少 Swagger/Knife4j 集成（PRD 6.5 要求接口文档自动生成） | V1.1 引入 |

---

## 三、前端代码评审

### 3.1 组件规范

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 组件命名 PascalCase | ✅ | `DeployProgressDialog`、`EnvConfirmDialog`、`StatusDot` |
| Composition API 使用 | ✅ | 所有页面使用 `<script setup>` |
| 图标使用 | ✅ | 使用 `@element-plus/icons-vue` |
| 无 var 声明 | ✅ | 全部使用 `ref`/`reactive`/`const` |

### 3.2 状态处理

| 检查项 | 结果 | 说明 |
|--------|------|------|
| Loading 状态 | ✅ | 各页面均有 `v-loading` |
| 空数据状态 | ⚠️ | 部分页面使用 `empty-text`，未统一使用 `el-empty` 组件 |
| 错误状态 | ❌ | `catch (e) {}` 空捕获，错误吞掉 |
| 二次确认 | ⚠️ | `EnvConfirmDialog` 组件实现正确，但未在生产环境操作中强制使用 |

### 3.3 API 调用匹配后端

| 检查项 | 结果 | 说明 |
|--------|------|------|
| RESTful 路径 | ✅ | `/api/services/{id}`、`/api/deploy/execute` 等 |
| HTTP 方法 | ✅ | GET/POST/PUT/DELETE 使用正确 |
| 分页参数 | ✅ | `pageNum`/`pageSize` 与后端一致 |
| 回退 API | ⚠️ | 前端使用 `/services/{id}/rollback`（POST + body），后端正确实现 |
| 发版 API | ⚠️ | `DeployController.executeDeploy` 使用 `Map` 而非 `DeployRequest` DTO |

### 3.4 UI 规范匹配度

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 配色方案 | ✅ | Dashboard 使用 UI 规范中的环境色（dev/test/staging/prod） |
| 间距系统 | ✅ | 基于 4px 的间距（8px/12px/16px/24px） |
| 字体规范 | ⚠️ | 部分页面使用内联字体大小，未完全使用 UI Token |
| 响应式适配 | ✅ | Dashboard 有 1440px/768px 断点适配 |
| 卡片样式 | ✅ | 与 UI 规范一致：白色背景 + 圆角 + 边框 |
| Tag 标签 | ⚠️ | 部分使用 Element Plus 默认颜色，未完全匹配 UI 规范 |

---

## 四、数据库检查

### 4.1 字段一致性

| 表 | PRD 定义 | db-schema 定义 | 代码实体 | 一致性 |
|----|---------|---------------|---------|--------|
| `t_project` | `name`(VARCHAR 64) | `project_code` + `project_name` | `projectName` | ⚠️ PRD 与 db-schema 字段名不同，代码使用 db-schema |
| `t_module` | `module_type`(VARCHAR) | `module_type`(TINYINT) | 无 `moduleType` 字段 | ⚠️ 代码中 Module 实体缺少 `moduleType` 字段 |
| `t_server` | `ssh_username` | `ssh_username` 不存在 | `sshUsername` | 🔴 db-schema 缺少 `ssh_username` 字段 |
| `t_service_instance` | `env_type` | 无 `env_type` | 无 `envType` | 🔴 服务实例缺少环境类型字段 |
| `t_deploy_record` | `version_no` | `version` + `deploy_no` | `version` + `deployNo` | ✅ 代码使用 db-schema |
| `t_operation_log` | `env_type` | 无 `env_type` | 无 `envType` | 🔴 缺少环境类型字段 |

### 4.2 索引合理性

| 表 | 索引 | 评价 |
|----|------|------|
| `t_deploy_record` | `idx_instance_id`, `idx_create_time`, `idx_status` | ✅ 覆盖查询场景 |
| `t_service_instance` | `idx_module_id`, `idx_server_id` | ✅ 合理 |
| `t_operation_log` | `idx_create_time`, `idx_user_id`, `idx_module` | ✅ 合理 |
| `t_deploy_schedule` | `uk_schedule_name`, `idx_status` | ✅ 合理 |

### 4.3 数据完整性

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 外键约束 | ✅ | 无物理外键（符合 MyBatis Plus 最佳实践） |
| 逻辑删除 | ✅ | 业务表统一使用 `deleted` 字段 |
| 审计字段 | ✅ | `create_time`/`update_time` 统一使用 |
| `t_operation_log` 无 deleted | ✅ | 日志表不做逻辑删除，正确 |

---

## 五、与之前评审对比

| 编号 | 首次评审问题 | 当前状态 | 说明 |
|------|-------------|---------|------|
| ARCH-001 | 编译构建执行位置 | ✅ 已修复 | 已在目标服务器执行 |
| ARCH-002 | 并发部署无锁 | ✅ 已修复 | `ReentrantLock` 按实例加锁 |
| ARCH-003 | 版本号格式 | ✅ 已修复 | `YYYYMMDD_序号_Tag` 格式 |
| ARCH-004 | 健康检查无重试 | ✅ 已修复 | 3 次重试，5 秒间隔 |
| ARCH-005 | 构建超时控制 | ⚠️ 部分 | 硬编码 300 秒，无配置化 |
| ARCH-006 | 回退使用 shell 管道 | ❌ 未修复 | `DeployService.rollback()` 仍使用 `ls -lt` 管道 |
| ARCH-007 | 不支持指定目标版本回退 | ⚠️ 部分 | `ServiceInstanceServiceImpl` 支持，`DeployService` 不支持 |
| ARCH-008 | WebSocket 断线进程未清理 | ✅ 已修复 | `@OnClose` 中清理 SSH 连接和命令 |
| SEC-001 | 密钥硬编码 | ✅ 已修复 | 环境变量注入 + 启动校验 |
| SEC-002 | SSH 主机验证跳过 | ✅ 已修复 | `AutoAcceptKnownHosts` TOFU 策略 |
| CODE-001 | Module 实体不规范 | ✅ 已修复 | 添加注解 + LocalDateTime |
| CODE-003 | CORS 通配符 | ❌ 未修复 | 仍为 `allowedOriginPatterns("*")` |

---

## 六、风险评估汇总

| # | 风险项 | 等级 | 影响 | 应对策略 |
|---|--------|------|------|----------|
| R1 | Shell 命令注入（CR-002） | 🔴 高 | 恶意输入可执行任意命令 | 路径参数合法性校验 + shell 转义 |
| R2 | WebSocket 无身份验证（CR-004） | 🔴 高 | 任意用户可查看服务器实时日志 | 添加 JWT 验证或一次性连接凭证 |
| R3 | CORS 通配符（CR-003） | 🔴 高 | 跨站请求伪造风险 | 限制为前端域名白名单 |
| R4 | 数据库密码硬编码（CR-005） | 🔴 高 | 配置泄露可直连数据库 | 环境变量注入 |
| R5 | 回退逻辑不一致（CR-001） | 🟡 中 | 不同 API 路径回退行为不同，用户困惑 | 统一回退实现 |
| R6 | 前端错误吞掉（CR-006） | 🟡 中 | 用户操作失败无感知 | 添加错误提示和状态回滚 |
| R7 | SSH 连接频繁创建（CR-012） | 🟡 中 | 部署性能差，每次部署额外 1-10 秒 | 部署流程复用 SSH 连接 |
| R8 | N+1 查询（CR-007） | 🟡 中 | 模块列表加载慢 | 批量查询接口 |
| R9 | 轮询效率低（CR-009） | 🟡 中 | 大量 API 请求浪费资源 | SSE/WebSocket 推送 |
| R10 | `new Thread()` 线程泄漏（CR-010） | 🟡 中 | 大量 WebSocket 连接耗尽线程 | 使用线程池 |
| R11 | `t_server` 缺少 `ssh_username`（DB-001） | 🟡 中 | 数据库表与代码不一致 | 添加缺失字段 |
| R12 | 钉钉通知未集成（CR-013） | 🟡 中 | PRD 核心功能缺失 | 在发版/回退成功后调用 |
| R13 | `OperationLog` 缺少 `env_type`（DB-003） | 🟢 低 | 无法按环境筛选操作日志 | 添加字段并填充 |

---

## 七、总体评审结论

### 最终结论：⚠️ **不通过**

### 不通过原因汇总（Blocking）

| 编号 | 严重度 | 问题描述 | 修复优先级 |
|------|--------|----------|-----------|
| CR-001 | 🔴 | 版本回退两条路径不一致，`DeployService.rollback()` 不支持指定目标版本 | P0 |
| CR-002 | 🔴 | 多处 Shell 命令拼接未经安全转义，存在注入风险 | P0 |
| CR-003 | 🔴 | CORS 通配符 + 允许凭证，安全配置错误 | P0 |
| CR-004 | 🔴 | WebSocket 无身份验证，任意用户可查看服务器日志 | P0 |
| CR-005 | 🔴 | 数据库密码硬编码在 `application.yml` | P0 |

### 建议修复项（Recommended）

| 编号 | 严重度 | 问题描述 | 修复优先级 |
|------|--------|----------|-----------|
| CR-006 | 🟡 | 前端 `catch (e) {}` 空捕获，错误无反馈 | P1 |
| CR-007 | 🟡 | N+1 查询，模块加载效率低 | P1 |
| CR-008 | 🟡 | `DeployController.executeDeploy` 使用 `Map` 而非 DTO | P1 |
| CR-009 | 🟡 | 发版进度轮询效率低（2 秒 × 2 次 API） | P2 |
| CR-010 | 🟡 | `LogWebSocket` 使用 `new Thread()` 创建线程 | P1 |
| CR-011 | 🟡 | `GlobalExceptionHandler` 错误消息硬编码 | P2 |
| CR-012 | 🟡 | SSH 连接频繁创建/关闭，部署性能差 | P1 |
| CR-013 | 🟡 | 部署线程被 `Thread.sleep()` 阻塞 | P2 |
| CR-014 | 🟡 | Cron 表达式无预校验 | P2 |
| CR-015 | 🟡 | `OperationLog` 缺少 `env_type` 字段 | P2 |

### 轻微问题（Minor）

| 编号 | 问题 | 优先级 |
|------|------|--------|
| CR-016 ~ CR-024 | 枚举缺口、JavaDoc 不一致、find 命令优先级、TODO 残留、交互不一致等 | P3 |

### 数据库问题

| 编号 | 问题 | 优先级 |
|------|------|--------|
| DB-001 | `t_server` 缺少 `ssh_username` 字段 | P1 |
| DB-002 | `t_service_instance` 缺少 `env_type` 字段 | P1 |
| DB-003 | `t_operation_log` 缺少 `env_type` 字段 | P2 |

---

## 八、下一步行动

1. **全栈工程师**：修复 CR-001（统一回退逻辑）、CR-002（Shell 注入防护）、CR-005（数据库密码环境变量）
2. **全栈工程师**：修复 CR-003（CORS 白名单）、CR-004（WebSocket 身份验证）
3. **全栈工程师**：修复 CR-006（前端错误处理）、CR-008（DTO 化）、CR-010（线程池）、CR-012（SSH 连接复用）
4. **DBA/全栈**：修复 DB-001/DB-002（数据库字段补全）
5. **架构师**：修复完成后进行二次评审

---

> **评审人签名**: 码哥（Tech Architect）
> **评审日期**: 2026-04-15
> **评审结论**: ⚠️ 不通过 — 5 个 Blocking 问题需修复后方可合并到 test
