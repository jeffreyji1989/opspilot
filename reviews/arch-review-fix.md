# OpsPilot 运维管理系统 — 架构评审二次评审报告（Blocking 问题修复复审）

> **评审人**: 技术架构师（码哥 / Tech Architect）
> **评审日期**: 2026-04-14
> **评审对象**: 功能分支 `v1.0-fix-blocking-20260414` 的 6 个 Blocking 问题修复代码
> **参考文档**:
> - 首次评审报告: `projects/opspilot/reviews/arch-review.md`
> - PRD: `projects/opspilot/prd/opspilot-prd.md`
> - 提交: `04e8846 fix(backend): 修复架构评审 6 个 Blocking 问题`

---

## 一、6 个 Blocking 问题逐项复审

---

### ARCH-001 ✅ 通过 — 编译构建改为 SSH 远程执行

**修复方案**: `pullCode()` / `buildProject()` / `packageArtifact()` 改为接受 `Server` 参数，通过 `sshManager.executeCommand(cmd, server, timeout)` 在目标服务器执行。

**复审结果**:

| 检查项 | 结果 | 说明 |
|--------|------|------|
| pullCode 远程执行 | ✅ | `sshManager.executeCommand(pullCmd, server, 120)` |
| buildProject 远程执行 | ✅ | `sshManager.executeCommand(fullCmd, server, 300)` |
| packageArtifact 远程查找 | ✅ | 远程 `find` + `downloadFile` 下载到本地暂存 |
| 远程构建目录创建 | ✅ | `remoteBuildDir = instance.getDeployPath() + "/_build_" + recordId` |
| 构建完成后清理 | ✅ | `finally` 块中清理远程目录和本地暂存目录 |
| 新增 downloadFile 方法 | ✅ | SshManager 新增 `downloadFile()` SCP 下载 |
| 编译通过 | ✅ | `mvn compile` 通过，无编译错误 |

**新发现问题**:

- **FIX-001 🟡**: `executeDeploy()` finally 块中清理远程构建目录使用 `sshManager.executeCommand("rm -rf " + remoteBuildDir, server, 30)`，`remoteBuildDir` 来自 `instance.getDeployPath()` 的拼接，未经过 shell 转义。若 deployPath 中包含空格或特殊字符可能导致 `rm -rf` 误删。建议使用 `rm -rf '%s'` 或先做路径合法性校验。

---

### ARCH-002 ✅ 通过 — 并发部署按实例加锁

**修复方案**: `ConcurrentHashMap<String, ReentrantLock>` 按实例 ID 维度加锁，`tryLock(30, MINUTES)` 防止死等。

**复审结果**:

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 锁粒度（按实例 ID） | ✅ | `lockKey = String.valueOf(instance.getId())` |
| 获取锁 + 超时 | ✅ | `tryLock(DEPLOY_LOCK_TIMEOUT_MINUTES, TimeUnit.MINUTES)` |
| InterruptedException 处理 | ✅ | 恢复中断状态 + 更新状态为 FAILED |
| tryLock 失败处理 | ✅ | 返回失败 + `updateProcessStatus(FAILED)` |
| finally 释放锁 | ✅ | `lock.unlock()` 在 finally 中 |
| 锁获取日志 | ✅ | 获取成功/失败/被中断均有日志 |
| 部署前 status 预检 | ✅ | `deploy()` 方法第 4 步检查 `processStatus == RUNNING` |

**新发现问题**:

- **FIX-002 🟡**: 首次评审建议使用 Redis 分布式锁（适用于多节点部署场景），当前 `ConcurrentHashMap + ReentrantLock` 为 JVM 级别锁，**仅在单节点部署时有效**。若 OpsPilot 以集群方式部署，同一实例仍可能被不同节点并发部署。建议标注此限制，或在 `application.yml` 中增加 `single-node` 模式说明。
- **FIX-003 🟡**: `deploy()` 方法中 status 预检（第 4 步）与 `executeDeploy()` 中获取锁之间存在竞态窗口：两个请求同时通过预检后，只有第一个获取到锁，第二个请求会在 `tryLock` 超时 30 分钟才返回失败。建议在 `executeDeploy()` 获取锁前先做一次 `processStatus` 检查，或缩短 `tryLock` 超时时间（如 30 秒而非 30 分钟）。

---

### ARCH-003 ✅ 通过 — 版本号格式 YYYYMMDD_序号_Tag

**修复方案**: 新增 `generateVersion()` 和 `getNextVersionSeq()` 方法，查询数据库当天该模块最大序号并 +1。

**复审结果**:

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 格式正确性 | ✅ | `String.format("%s_%03d_%s", dateStr, seq, tag)` → `20260414_001_moduleName` |
| 序号计算 | ✅ | 数据库查询 + 字符串解析提取序号 |
| 排除回退记录 | ✅ | `ne(DeployRecord::getDeployType, "rollback")` |
| 异常回退 | ✅ | `catch` 中默认返回 1 |
| 日志记录 | ✅ | 创建部署记录时打印 `recordId + version` |
| switchVersion 使用版本号 | ✅ | `versionDir = deployPath + "/versions/" + version` |

**新发现问题**:

- **FIX-004 🟡**: 版本格式示例 `20260414_001_v1.0.0` 中 Tag 使用了 `v` 前缀，但代码中 `tag = module.getModuleName()` 直接取模块名（如 `order-service`），不含 `v` 前缀。PRD 8.2 原文为 `{YYYYMMDD}_{序号}_{Tag名}`，代码实现与 PRD 一致（Tag 名 ≠ 版本号），但 JavaDoc 示例有误，建议修正示例。
- **FIX-005 🟡**: `getNextVersionSeq()` 中 `likeRight(DeployRecord::getVersion, versionPrefix)` 在 MySQL 中会转为 `LIKE '20260414_%'`，该模式无法利用索引（右前缀通配符导致全表扫描）。当 `t_deploy_record` 数据量增大后可能成为性能瓶颈。建议增加 `date` 字段或改用 `>= / <` 范围查询。

---

### SEC-001 ✅ 通过 — 密钥通过环境变量注入 + @PostConstruct 校验

**修复方案**: AES 和 JWT 密钥改为 `${ENV_VAR:default}` 注入，`@PostConstruct` 中校验默认值并拒绝启动。

**复审结果**:

| 检查项 | 结果 | 说明 |
|--------|------|------|
| application.yml 占位符 | ✅ | `${OPSPILOT_JWT_SECRET:Default}` / `${OPSPILOT_AES_KEY:Default}` |
| AesGcmUtil @PostConstruct | ✅ | `validateAesKey()` 校验 `DEFAULT_AES_KEY` 相等即抛 `IllegalStateException` |
| JwtUtil @PostConstruct | ✅ | `validateJwtSecret()` 校验 `DEFAULT_JWT_SECRET` 相等即抛 `IllegalStateException` |
| 默认值未被泄露 | ✅ | 异常信息不包含实际密钥值 |
| 错误信息明确 | ✅ | 包含环境变量名和生成示例命令 |
| application.yml 注释 | ✅ | 安全警告 + 生成命令注释 |

---

### SEC-002 ✅ 通过 — SSH 主机密钥验证 TOFU 策略

**修复方案**: 自定义 `AutoAcceptKnownHosts` 继承 `OpenSSHKnownHosts`，首次连接自动信任并保存，密钥变更拒绝连接。

**复审结果**:

| 检查项 | 结果 | 说明 |
|--------|------|------|
| 移除 PromiscuousVerifier | ✅ | 不再无条件跳过验证 |
| AutoAcceptKnownHosts 实现 | ✅ | 继承 `OpenSSHKnownHosts`，重写两个核心方法 |
| 首次连接处理 | ✅ | `hostKeyUnverifiableAction()` → 自动保存密钥 + 返回 true |
| 密钥变更处理 | ✅ | `hostKeyChangedAction()` → 记录日志 + 返回 false |
| known_hosts 文件路径 | ✅ | `~/.opspilot/ssh/known_hosts` |
| 文件自动创建 | ✅ | `@PostConstruct init()` 创建目录和文件 |
| 回退机制 | ✅ | 加载失败时回退到 `PromiscuousVerifier`（有日志告警） |
| 密钥保存格式 | ✅ | `hostname KeyType base64`，符合 OpenSSH known_hosts 格式 |

**新发现问题**:

- **FIX-006 🟢**: `createHostKeyVerifier()` 回退到 `PromiscuousVerifier` 时仅打印 WARN 日志，不阻止启动。在严格安全要求的场景下，建议将回退行为改为抛异常（可配置）。当前行为可接受，不影响本次评审结论。

---

### CODE-001 ✅ 通过 — Module 实体规范化

**修复方案**: 添加 `@Data` + `@TableName("t_module")` + `@TableId` + `@TableLogic`，时间字段改为 `LocalDateTime`，保留兼容方法。

**复审结果**:

| 检查项 | 结果 | 说明 |
|--------|------|------|
| @Data 注解 | ✅ | Lombok 自动生成 getter/setter/toString/equals/hashCode |
| @TableName | ✅ | `@TableName("t_module")` 映射正确 |
| @TableId | ✅ | `@TableId(type = IdType.AUTO)` 主键自增 |
| @TableLogic | ✅ | `@TableLogic` 在 deleted 字段上 |
| LocalDateTime 字段 | ✅ | `createTime` 和 `updateTime` 改为 `LocalDateTime` |
| 兼容方法 | ✅ | `setCreatedTime(Date)` / `getCreatedTime()` 自动转换 |
| 字段注释 | ✅ | 每个字段有中文注释 |
| 手动 getter/setter 已删除 | ✅ | 移除冗余代码，由 Lombok 生成 |
| 编译通过 | ✅ | `mvn compile` 通过 |

**新发现问题**:

- **FIX-007 🟢**: `Module` 实体将字段名改为 `createTime` / `updateTime`（camelCase），而其他实体（如 `DeployRecord`）通过兼容方法映射了 `createdTime` → `createTime`。这种命名风格统一值得肯定，但兼容方法中使用了 `java.util.Date` 的 `toInstant()` 转换，时区依赖 `ZoneId.systemDefault()`。若服务器时区与应用时区不一致可能导致时间偏移。建议在 `@Configuration` 中统一设置 `spring.jackson.time-zone` 或使用 `@JsonFormat`。

---

## 二、代码编译验证

```bash
cd projects/opspilot/src/opspilot-backend && mvn compile
```

**结果**: ✅ 编译通过（无 ERROR，仅 Lombok 对 JDK 新版本的 WARNING，不影响功能）

---

## 三、新发现问题汇总

| 编号 | 严重度 | 关联问题 | 描述 | 建议 |
|------|--------|----------|------|------|
| FIX-001 | 🟡 中 | ARCH-001 | `rm -rf` 命令拼接未经 shell 转义，deployPath 含特殊字符时可能误删 | 使用 `rm -rf '%s'` 或先校验路径合法性 |
| FIX-002 | 🟡 中 | ARCH-002 | 锁为 JVM 级别，多节点部署无效 | 标注单节点限制，后续引入 Redis 分布式锁 |
| FIX-003 | 🟡 中 | ARCH-002 | 预检与加锁之间竞态窗口，`tryLock` 超时 30 分钟过长 | 缩短 `tryLock` 超时至 30 秒，或在加锁前二次检查 status |
| FIX-004 | 🟢 低 | ARCH-003 | JavaDoc 示例 `20260414_001_v1.0.0` 与实际 `20260414_001_moduleName` 不一致 | 修正 JavaDoc 示例 |
| FIX-005 | 🟡 中 | ARCH-003 | `LIKE '20260414_%'` 查询无法利用索引，数据量大时性能差 | 增加 `deploy_date` 字段或改用范围查询 |
| FIX-006 | 🟢 低 | SEC-002 | known_hosts 加载失败回退到 PromiscuousVerifier | 建议可配置为严格模式（抛异常） |
| FIX-007 | 🟢 低 | CODE-001 | LocalDateTime 转换依赖系统时区 | 统一时区配置 |

---

## 四、总体评审结论

### 最终结论：✅ **通过**

### 结论说明

| 编号 | 问题 | 修复评审 | 结论 |
|------|------|----------|------|
| ARCH-001 | 编译构建执行位置 | ✅ 修复正确 | 通过 |
| ARCH-002 | 并发部署无锁 | ✅ 修复正确（单节点有效） | 通过 |
| ARCH-003 | 版本号格式 | ✅ 修复正确 | 通过 |
| SEC-001 | 密钥硬编码 | ✅ 修复正确 | 通过 |
| SEC-002 | SSH 跳过验证 | ✅ 修复正确 | 通过 |
| CODE-001 | Module 实体不规范 | ✅ 修复正确 | 通过 |

**6 个 Blocking 问题均已正确修复**，代码编译通过，可以进入下一阶段。

### 建议

1. **FIX-002（多节点分布式锁）** 建议在进入生产前引入 Redis 分布式锁，当前 JVM 锁仅适用于单节点部署
2. **FIX-003（tryLock 超时时间）** 建议将 30 分钟缩短至 30 秒，减少用户等待时间
3. **FIX-005（版本号查询性能）** 建议在 V1.1 版本中增加 `deploy_date` 字段优化查询
4. **FIX-001（shell 注入）** 建议对 deployPath 做合法性校验后再拼接 shell 命令

---

> **评审人签名**: 码哥（Tech Architect）
> **评审日期**: 2026-04-14
> **评审结论**: ✅ 通过 — 6 个 Blocking 问题均已修复，可进入下一阶段
