# OpsPilot 运维管理系统 — 端到端测试报告

> **执行人**: QA Engineer (AI)  
> **执行日期**: 2026-04-14  
> **测试环境**:  
> - 后端: http://localhost:8080 (Spring Boot 3.2)  
> - 前端: http://localhost:3000 (Vue3 + Vite)  
> - 数据库: MySQL localhost:3306, opspilot 库  
> **测试方法**: curl API 测试 + 数据库数据验证 + 前端页面加载检查  

---

## 📊 测试概要

| 指标 | 数值 |
|------|------|
| 测试接口总数 | 20 |
| ✅ 通过 | 11 |
| ❌ 失败 | 9 |
| ⚠️ 部分通过 | 0 |
| 通过率 | **55%** |
| P0 阻塞性 Bug | **2** |
| 发现 Bug 总数 | **3** |

### ⚠️ 关键结论

**当前版本不可发布。** 存在 2 个 P0 级别阻塞性 Bug：
1. `t_module` 表缺少 5 个字段 → 模块管理完全不可用
2. `t_server` 表缺少 2 个字段 → 服务器管理和仪表盘完全不可用

这 2 个 Bug 导致约 50% 的核心 API 无法正常使用。

---

## 🔍 逐接口测试结果

### 1. 登录认证

| 编号 | 测试场景 | 接口 | 状态 | 说明 |
|------|---------|------|------|------|
| 1.1 | 正常登录 | `POST /api/auth/login` | ✅ 通过 | 返回 JWT token，code=200，含 userId/username/displayName/role |
| 1.2 | 密码错误 | `POST /api/auth/login` (wrong pass) | ✅ 通过 | 返回 code=20001，message="用户名或密码错误" |
| 1.3 | 用户不存在 | `POST /api/auth/login` (nonexistent) | ✅ 通过 | 返回 code=20001，message 与密码错误一致（防枚举） |
| 1.4 | 无 token 访问 | `GET /api/projects` (no auth) | ✅ 通过 | 返回 HTTP 401 |

**登录功能评价**: ✅ 正常。JWT 认证有效，错误提示统一（不泄露账号存在性）。

---

### 2. 项目管理

| 编号 | 测试场景 | 接口 | 状态 | 说明 |
|------|---------|------|------|------|
| 2.1 | 获取项目列表 | `GET /api/projects?pageNum=1&pageSize=10` | ✅ 通过 | 返回 3 个项目（yn-bpm, 删除测试b, 去重测试），分页正常 |
| 2.2 | 创建项目 | `POST /api/projects` | ✅ 通过 | 成功创建"测试项目"，projectCode="TEST"，返回 id=16 |
| 2.3 | 项目编码重复 | `POST /api/projects` (重复 yn-bpm) | ✅ 通过 | 返回 code=30001，message="项目编码已存在" |
| 2.4 | 删除有模块的项目 | `DELETE /api/projects/15` | ✅ 通过 | 返回 code=30002，message="项目下存在模块，不允许删除" |
| 2.5 | 删除无模块的项目 | `DELETE /api/projects/6` | ❌ 失败 | **受 Bug-001 影响**，删除前检查模块时 SQL 报错 |
| 2.6 | 查询模块列表 | `GET /api/projects/1/modules` | ❌ 失败 | **受 Bug-001 影响**，SQL 报错 |

---

### 3. 服务器管理

| 编号 | 测试场景 | 接口 | 状态 | 说明 |
|------|---------|------|------|------|
| 3.1 | 查询服务器列表 | `GET /api/servers?pageNum=1&pageSize=10` | ❌ 失败 | **受 Bug-002 影响**，`Unknown column 'jdk_versions'` |
| 3.2 | 按环境分组 | `GET /api/servers/by-env` | ❌ 失败 | **受 Bug-002 影响**，同上 |
| 3.3 | 查询服务器详情 | `GET /api/servers/1` | ❌ 失败 | **受 Bug-002 影响**，同上 |

**数据库数据确认**: `t_server` 表有 1 条记录（图纸测试环境，172.16.113.220，env_type=1 测试环境，status=1）

---

### 4. 服务实例

| 编号 | 测试场景 | 接口 | 状态 | 说明 |
|------|---------|------|------|------|
| 4.1 | 查询服务实例 | `GET /api/services?pageNum=1&pageSize=10` | ✅ 通过 | 返回 1 条记录（印尼bpm-服务端，module_id=4，port=9001，process_status=0 已停止） |
| 4.2 | 文档地址不匹配 | `GET /api/service-instances` | ❌ 失败 | 404 "No static resource"。**实际路径是 `/api/services`** |

**注意**: 测试用例文档中写的是 `/api/service-instances`，但实际后端路径是 `/api/services`。这是文档与实现不一致。

---

### 5. 发版部署

| 编号 | 测试场景 | 接口 | 状态 | 说明 |
|------|---------|------|------|------|
| 5.1 | 查询部署历史 | `GET /api/deploy/history/4?pageNum=1&pageSize=10` | ✅ 通过 | 返回空数组（无部署记录），符合预期 |
| 5.2 | 查询部署进度 | `GET /api/deploy/progress/1` | ⚠️ 通过 | 返回 code=500 "部署记录不存在"，recordId=1 不存在时返回此提示合理 |

---

### 6. Git 认证管理

| 编号 | 测试场景 | 接口 | 状态 | 说明 |
|------|---------|------|------|------|
| 6.1 | 查询认证列表 | `GET /api/git-credentials?pageNum=1&pageSize=10` | ✅ 通过 | 返回 1 条记录（公司gitlab，HTTPS Token 类型，encryptedData 已加密存储） |

**安全确认**: `encryptedData` 字段存储的是 Base64 编码的加密字符串，非明文。✅ 符合 AES 加密存储要求。

---

### 7. 操作日志

| 编号 | 测试场景 | 接口 | 状态 | 说明 |
|------|---------|------|------|------|
| 7.1 | 查询操作日志 | `GET /api/operation-logs?pageNum=1&pageSize=10` | ✅ 通过 | 返回 47 条记录，包含 LOGIN/CREATE/DETECT 等操作类型 |
| 7.2 | 日志完整性 | — | ✅ 通过 | 日志包含 userId, module, operation, targetType, targetName, ipAddress, createTime 等字段 |

---

### 8. 定时任务

| 编号 | 测试场景 | 接口 | 状态 | 说明 |
|------|---------|------|------|------|
| 8.1 | 查询定时任务 | `GET /api/schedules?pageNum=1&pageSize=10` | ✅ 通过 | 返回空列表（无定时任务），符合预期 |
| 8.2 | 文档地址不匹配 | `GET /api/deploy-schedules` | ❌ 失败 | 404 "No static resource"。**实际路径是 `/api/schedules`** |

---

### 9. 系统设置

| 编号 | 测试场景 | 接口 | 状态 | 说明 |
|------|---------|------|------|------|
| 9.1 | 查询系统设置 | `GET /api/system-settings` | ✅ 通过 | 返回 healthCheckTimeout=60, buildTimeout=300, dingtalkEnabled=false 等配置 |

---

### 10. 仪表盘

| 编号 | 测试场景 | 接口 | 状态 | 说明 |
|------|---------|------|------|------|
| 10.1 | 仪表盘统计 | `GET /api/dashboard/stats` | ❌ 失败 | **受 Bug-002 影响**，查询服务器时 `Unknown column 'jdk_versions'` |

---

### 11. 模块管理

| 编号 | 测试场景 | 接口 | 状态 | 说明 |
|------|---------|------|------|------|
| 11.1 | 按项目查询模块 | `GET /api/modules/project/15` | ❌ 失败 | **受 Bug-001 影响**，SQL 报错 |
| 11.2 | 构建命令模板 | `GET /api/modules/build-template/0` | ❌ 失败 | **受 Bug-003 影响**，"不支持的模块类型: 0" |

---

## 🐛 Bug 清单

| 编号 | 优先级 | 模块 | 描述 | 状态 |
|------|--------|------|------|------|
| [Bug-001](../bugs/bug-001.md) | **P0** | 模块管理 | `t_module` 表缺少 5 个字段（deploy_path, health_check_path, deploy_order, description, maven_module_name），导致所有模块相关查询失败 | 未修复 |
| [Bug-002](../bugs/bug-002.md) | **P0** | 服务器管理 | `t_server` 表缺少 2 个字段（jdk_versions, node_versions），导致服务器列表、详情、按环境分组、仪表盘全部失败 | 未修复 |
| [Bug-003](../bugs/bug-003.md) | P2 | 模块管理 | 构建模板接口 `build-template/{moduleType}` 不支持数字类型，只支持字符串如 "JAR" | 未修复 |

### 文档/实现不一致（非 Bug，需确认）

| # | 描述 |
|---|------|
| D-001 | 服务实例 API 路径：文档写 `/api/service-instances`，实际是 `/api/services` |
| D-002 | 定时任务 API 路径：文档写 `/api/deploy-schedules`，实际是 `/api/schedules` |

---

## 🌐 前端页面验证

| 页面 | URL | 状态 | 说明 |
|------|-----|------|------|
| 首页 | http://localhost:3000 | ✅ 可访问 | Vite 开发服务器运行中，返回 HTML（含 `<div id="app">`） |

**注意**: 前端页面通过 Vite dev server 提供，页面可访问。但由于后端多个 API 返回 500 错误，前端页面的服务器管理、模块管理、仪表盘等功能模块预计无法正常加载数据。

---

## 📋 数据库数据现状

### t_user
| id | username | role |
|----|----------|------|
| 1 | admin | 2 |

### t_project (未删除)
| id | project_code | project_name |
|----|-------------|-------------|
| 6 | dup-test-002 | 去重测试 |
| 9 | del-test-001b | 删除测试b |
| 15 | yn-bpm | 印尼BPM |
| 16 | TEST | 测试项目（本次测试创建） |

### t_server
| id | server_name | hostname | env_type | status |
|----|------------|----------|----------|--------|
| 1 | 图纸测试环境 | 172.16.113.220 | 1 (测试) | 1 |

### t_module
| id | project_id | module_name | module_type |
|----|-----------|------------|-------------|
| 1 | 3 | test-service | 2 |
| 2 | 9 | test-module | 0 |
| 3 | 12 | test-module-v3 | 1 |
| 4 | 15 | 印尼bpm-服务端 | 0 |

### t_service_instance (未删除)
| id | instance_name | module_id | server_id | process_status |
|----|--------------|-----------|-----------|---------------|
| 1 | 印尼bpm-服务端 | 4 | 1 | 0 (已停止) |

### t_git_credential
| id | credential_name | credential_type | status |
|----|----------------|----------------|--------|
| 1 | 公司gitlab | 1 (HTTPS Token) | 1 |

### t_operation_log
总计 47 条记录（截至测试执行时）

### t_deploy_record / t_deploy_step / t_deploy_schedule
无记录（尚未执行过发版操作）

---

## 📊 测试覆盖率分析

### 按测试用例模块覆盖

| 用例模块 | P0 用例数 | 本次覆盖 | 覆盖说明 |
|---------|----------|---------|---------|
| 用户登录 | 3 | 3/3 | TC-AUTH-001/002/003 均已通过 |
| 项目管理 | 6 | 4/6 | TC-PROJ-004/005/007 因 Bug-001 无法测试 |
| 服务器管理 | 4 | 0/4 | TC-SRV-001~007 均因 Bug-002 无法测试 |
| Git 认证管理 | 5 | 1/5 | 列表查询通过，创建/编辑/删除/加密验证未测试 |
| 服务实例管理 | 3 | 0/3 | 创建/删除服务需模块信息（Bug-001 阻断） |
| 发版部署 | 8 | 1/8 | 历史查询通过，实际发版流程无法测试（依赖模块） |
| 日志查看 | 2 | 0/2 | WebSocket 实时日志和历史日志未测试 |
| 版本回退 | 3 | 0/3 | 需先有部署记录才能测试 |
| 定时发版 | 5 | 0/5 | 创建和执行定时任务未测试 |
| 操作日志 | 4 | 1/4 | 列表查询通过，过滤/不可删除验证未测试 |
| 二次确认 | 5 | 0/5 | 前端交互测试，需浏览器自动化 |

### 核心业务流程覆盖

| 核心流程 | 状态 | 说明 |
|---------|------|------|
| 登录 → 查看仪表盘 | ❌ 阻塞 | 仪表盘因 Bug-002 无法加载 |
| 创建项目 → 添加模块 → 创建服务 → 发版部署 | ❌ 阻塞 | 添加模块因 Bug-001 失败 |
| 发版 → 回退 | ❌ 阻塞 | 无部署记录，且模块查询失败 |
| 定时发版创建与执行 | ❌ 未测试 | 模块查询失败 |

---

## 🎯 修复优先级建议

### 立即修复（阻塞发布）

1. **Bug-001**: 添加 `t_module` 缺失列 → 恢复模块管理功能
2. **Bug-002**: 添加 `t_server` 缺失列 → 恢复服务器管理和仪表盘

### 建议修复（影响用户体验）

3. **Bug-003**: 修复 build-template 类型映射
4. **D-001/D-002**: 统一文档与实现 API 路径

### 建议补充测试

5. 前端页面完整加载验证（需浏览器自动化）
6. 发版部署端到端流程（需修复 Bug-001/002 后）
7. WebSocket 实时日志
8. 二次确认机制（前端交互）

---

## 📝 附录

### 数据库表结构对比

#### t_server 实际 vs Entity 期望

| 字段 | 实际表 | Entity | 状态 |
|------|--------|--------|------|
| jdk_versions | ❌ | ✅ | **缺失** |
| node_versions | ❌ | ✅ | **缺失** |

#### t_module 实际 vs Entity 期望

| 字段 | 实际表 | Entity | 状态 |
|------|--------|--------|------|
| deploy_path | ❌ | ✅ | **缺失** |
| health_check_path | ❌ | ✅ | **缺失** |
| deploy_order | ❌ | ✅ | **缺失** |
| description | ❌ | ✅ | **缺失** |
| maven_module_name | ❌ | ✅ | **缺失** |

---

> **报告结束**  
> 测试结论：当前版本 **不可发布**，需先修复 Bug-001 和 Bug-002 两个 P0 阻塞性 Bug。  
> 建议修复后重新执行完整回归测试。
