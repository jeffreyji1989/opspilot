# OpsPilot 运维管理系统 — PRD 文档

> **文档版本**: v1.0  
> **创建日期**: 2026-04-12  
> **文档状态**: 初稿待审  
> **目标读者**: 产品、架构师、前后端开发、QA

---

## 1. 功能概述

### 1.1 项目定位

**OpsPilot**（Operations Master）是一套轻量级、开箱即用的**运维管理系统**，用于管理多项目、多服务器、多环境的发版部署和运行监控。系统通过 Web 界面将原本需要 SSH 登录服务器手动操作的运维流程（拉代码 → 编译构建 → 部署 → 重启 → 健康检查）**标准化、自动化、可视化**。

### 1.2 目标用户

| 角色 | 描述 | 核心诉求 |
|------|------|----------|
| 后端开发 | Java/Spring Boot 开发 | 快速发版、一键回退、日志查看 |
| 前端开发 | Vue/React/Node.js 开发 | 前端构建部署、环境管理 |
| 全栈开发 | 同时负责前后端 | 统一管理多个服务的发版流程 |
| 运维（兼职） | 小团队中兼职运维的开发 | 服务器状态监控、钉钉告警 |

**团队规模**: 5-20 人小团队，无专职运维。

### 1.3 核心价值

1. **降低运维门槛**: 开发不需要 SSH 到服务器手动敲命令，Web 界面点几下即可完成发版
2. **减少人为错误**: 标准化部署流程，避免手工操作遗漏步骤
3. **提升效率**: 从 SSH 登录 → 拉代码 → 编译 → 部署的 15-30 分钟，缩短为 1 次点击
4. **可追溯**: 每次发版、重启、回退都有完整记录
5. **多环境隔离**: 开发/测试/预发/生产环境一目了然，生产操作有安全保护
6. **轻量开箱即用**: 无需 K8s、Jenkins 等重型工具，Spring Boot 部署即可运行

### 1.4 技术栈

| 层级 | 技术选型 |
|------|----------|
| 后端框架 | Spring Boot 3.x |
| ORM | MyBatis Plus |
| 前端框架 | Vue 3 + Composition API |
| UI 组件库 | Element Plus |
| 数据库 | MySQL 8.x |
| 缓存 | Redis |
| 认证 | 用户名 + 密码（Session/JWT） |
| 实时通信 | WebSocket（日志实时推送） |
| SSH 连接 | JSch / SSHJ |
| 定时任务 | Spring Scheduled / Quartz |
| 告警 | 钉钉机器人 Webhook |

### 1.5 不在 V1 范围内（后续迭代）

- 多用户角色权限管理（RBAC）
- 容器化部署（Docker/K8s）
- CI/CD 流水线可视化编排
- 应用性能监控（APM）集成
- 多租户支持
- 灰度发布 / 蓝绿部署

---

## 2. 用户角色与使用场景

### 2.1 用户角色

V1 版本系统**无细粒度角色权限**，所有登录用户拥有相同操作权限。后续版本引入 RBAC 后扩展。

### 2.2 核心使用场景

#### 场景 1：日常发版部署

> **用户**: 后端开发小李  
> **背景**: 修复了一个线上 Bug，需要把代码发布到测试环境和生产环境  
> **操作路径**:
> 1. 登录 OpsPilot → 进入「服务管理」
> 2. 找到目标服务（如 `order-service`），点击进入详情页
> 3. 点击「🚀 发版部署」
> 4. 选择目标分支（如 `bugfix/order-timeout`）
> 5. 点击「开始部署」
> 6. 实时查看部署进度（拉代码 → 编译 → 构建 → 部署 → 健康检查）
> 7. 部署完成后，确认服务正常运行

#### 场景 2：紧急回退

> **用户**: 全栈开发小王  
> **背景**: 刚发布的新版本出现严重 Bug，需要快速回退到上一个稳定版本  
> **操作路径**:
> 1. 进入「服务管理」→ 选择服务
> 2. 点击「⏪ 版本回退」
> 3. 在发版记录中选择要回退的历史版本
> 4. 系统自动切换软链 → 重启 → 健康检查
> 5. 回退完成后，钉钉通知相关人员

#### 场景 3：添加新服务器

> **用户**: 全栈开发  
> **背景**: 新申请了一台测试服务器，需要添加到系统中  
> **操作路径**:
> 1. 进入「服务器管理」
> 2. 点击「添加服务器」
> 3. 填写 IP、端口、用户名、密码、环境类型（dev/test/staging/prod）
> 4. 系统自动建立 SSH 互信并探测环境信息
> 5. 服务器出现在列表中，可用于创建服务

#### 场景 4：查看服务日志排查问题

> **用户**: 后端开发  
> **背景**: 服务报错了，需要查看日志定位问题  
> **操作路径**:
> 1. 进入「服务管理」→ 选择服务
> 2. 点击「📋 查看日志」
> 3. 实时日志自动推送，或切换到「历史日志」搜索关键字
> 4. 找到错误堆栈，定位问题

#### 场景 5：定时发版

> **用户**: 全栈开发  
> **背景**: 需要在凌晨 2 点低峰期自动发布到生产环境  
> **操作路径**:
> 1. 进入「服务管理」→ 选择服务
> 2. 点击「⏰ 定时发版」
> 3. 设置定时时间/Cron 表达式、目标分支、失败回退策略
> 4. 开启钉钉通知
> 5. 定时任务到期自动执行，结果通过钉钉推送

---

## 3. 详细功能说明

### 3.1 系统菜单结构

```
OpsPilot 运维管理系统
├─ 🏠 仪表盘          — 全局概览、统计指标、快捷入口
├─ 📁 项目管理        — 项目与模块的增删改查
├─ 🖥️ 服务器管理      — 服务器添加、状态探测、分组管理
├─ 🔑 Git 认证管理    — SSH Key / HTTPS Token 管理
├─ 🔧 服务管理        — 服务实例创建与运维操作（核心）
├─ 📜 操作日志        — 全量操作审计
└─ ⚙️ 系统设置        — 系统配置、钉钉通知配置
```

---

### 3.2 🏠 仪表盘

#### 功能描述

系统首页，提供全局视角的运维状态概览，帮助开发人员快速了解系统运行情况。

#### 功能清单

| 功能 | 说明 |
|------|------|
| 统计卡片 | 项目总数、服务器总数、服务实例总数、今日操作次数 |
| 环境分布 | 各环境（dev/test/staging/prod）的服务器数量分布 |
| 服务健康 | 各服务的运行/停止状态概览（绿色/红色标记） |
| 最近操作 | 最近 10 条操作记录（操作人、类型、时间、状态） |
| 快捷入口 | 常用操作快捷按钮（发版、重启、查看日志等） |
| 告警信息 | 最近的钉钉告警通知摘要 |

---

### 3.3 📁 项目管理

#### 3.3.1 项目列表

**页面布局**: 表格展示 + 顶部搜索栏

| 字段 | 类型 | 说明 |
|------|------|------|
| 项目名称 | String | 唯一标识，如 `order-system` |
| 负责人 | String | 项目负责人姓名 |
| 所属业务 | String | 归属业务线，如 `电商交易` |
| 标签 | Array | 多个标签，支持按标签筛选 |
| 模块数量 | Number | 该项目下模块个数 |
| 创建时间 | DateTime | — |
| 操作 | — | 编辑 / 删除 / 管理模块 |

**搜索条件**: 项目名称（模糊）、负责人、标签

#### 3.3.2 项目 CRUD

- **新增**: 弹窗表单，填写项目名称、负责人、所属业务、标签
- **编辑**: 同上
- **删除**: 二次确认，仅当项目下无模块、无关联服务时允许删除

#### 3.3.3 模块管理

每个项目下可添加多个模块，模块是**发版部署的基本单元**。

**模块列表**:

| 字段 | 类型 | 说明 |
|------|------|------|
| 模块名称 | String | 如 `order-service`、`admin-frontend` |
| 模块类型 | Enum | `JAR` / `WAR` / `Vue` / `React` / `Node.js` / `Android` / `Flutter` |
| Git 仓库地址 | String | 如 `git@github.com:xxx/order.git` |
| 默认分支 | String | 如 `main` / `develop` |
| 仓库子路径 | String | Monorepo 场景使用，如 `backend/order-service`，可空 |
| Maven 模块名 | String | Maven 多模块场景，如 `order-service`，可空 |
| JDK 版本 | Enum | `8` / `11` / `17` / `21`，仅 Java 类型需要 |
| Node 版本 | Enum | `14` / `16` / `18` / `20`，仅前端/Node 类型需要 |
| 构建命令 | String | 如 `mvn clean package -DskipTests` 或 `npm run build` |
| 构建产物路径 | String | 如 `target/order-service.jar` 或 `dist/` |
| Git 认证 ID | Long | 关联 Git 认证记录 |

**模块类型与构建说明**:

| 模块类型 | 构建工具 | 默认构建命令 | 产物类型 |
|----------|----------|-------------|----------|
| Spring Boot JAR | Maven | `mvn clean package -pl {模块名} -am -DskipTests` | JAR |
| WAR | Maven | `mvn clean package -pl {模块名} -am -DskipTests` | WAR |
| Vue | npm | `npm install && npm run build` | dist/ 目录 |
| React | npm | `npm install && npm run build` | build/ 目录 |
| Node.js | npm | `npm install` | 无产物（直接运行） |
| Android | Gradle | `./gradlew assembleRelease` | APK |
| Flutter | Flutter CLI | `flutter build apk` | APK |

**Maven 多模块处理**:
- 当模块类型为 JAR/WAR 且配置了 Maven 模块名时，使用 `-pl` 和 `-am` 参数按需编译
- 编译命令自动拼装：`mvn clean package -pl {mavenModuleName} -am -DskipTests`
- 若仓库子路径有值，则 `cd {子路径}` 后执行编译

**Monorepo 处理**:
- 同一 Git 仓库可配置多个模块，通过「仓库子路径」区分
- 各模块共享同一个 Git 仓库和认证信息
- 拉代码时共用同一份代码仓库

---

### 3.4 🖥️ 服务器管理

#### 3.4.1 服务器列表

**页面布局**: 按环境分组的卡片/表格展示

| 字段 | 类型 | 说明 |
|------|------|------|
| 主机名 | String | 用户自定义名称，如 `prod-order-01` |
| IP 地址 | String | 服务器 IP |
| SSH 端口 | Number | 默认 22 |
| 用户名 | String | SSH 登录用户名 |
| 环境类型 | Enum | `dev` / `test` / `staging` / `prod` |
| 状态 | Enum | `在线` / `离线` / `探测中` |
| 已部署服务数 | Number | 该服务器上运行的服务实例数量 |
| 创建时间 | DateTime | — |

**环境分组与样式**:

```
┌─ 🟢 开发环境 (dev) — 3 台
├─ 🟡 测试环境 (test) — 2 台
├─ 🟠 预发环境 (staging) — 1 台
└─ 🔴 生产环境 (prod) — 3 台  ← 红色边框/图标标记
```

#### 3.4.2 添加服务器

**流程**:

1. 填写表单：主机名、IP 地址、SSH 端口（默认 22）、用户名、密码、环境类型
2. 选择环境类型为 `prod` 时，**弹出二次确认对话框**：
   ```
   ⚠️ 您正在添加生产环境服务器

   主机名: prod-order-01
   IP 地址: 192.168.1.100
   环境: 生产（prod）

   生产环境操作具有高风险，请确认信息无误。

   [ 取消 ]  [ 我确认，继续添加 ]
   ```
3. 点击确认后，系统：
   - 通过 SSH 密码连接服务器
   - 自动部署公钥，建立 SSH 互信（后续不再需要密码）
   - 密码**不入库**，连接成功后立即丢弃
   - 自动探测服务器环境信息（见下方）
4. 探测完成后显示探测结果，确认无误后保存

#### 3.4.3 环境自动探测

添加服务器时自动执行以下探测脚本：

| 探测项 | 探测方式 | 返回示例 |
|--------|----------|----------|
| OS 信息 | `cat /etc/os-release` | `CentOS 7.9` |
| 已安装 JDK | `java -version` / 检查 `jenv versions` | `1.8.0_301, 11.0.12, 17.0.1` |
| 已安装 Node.js | `node -v` / 检查 `nvm ls` | `v16.14.0, v18.17.0` |
| 是否安装 NVM | `which nvm` / `nvm --version` | `0.39.0` |
| 是否安装 jenv | `which jenv` / `jenv --version` | `0.5.5` |
| 是否安装 Docker | `docker --version` | `Docker 24.0.5` |

探测结果在服务器详情页展示。

#### 3.4.4 服务器详情页

| 区块 | 内容 |
|------|------|
| 基本信息 | 主机名、IP、端口、用户名、环境类型 |
| 环境探测结果 | OS、JDK 列表、Node 列表、NVM/jenv/Docker 安装状态 |
| 已部署服务 | 该服务器上运行的服务实例列表 |
| 操作 | 编辑、删除（有服务运行中时禁止删除）、重新探测 |

---

### 3.5 🔑 Git 认证管理

#### 功能描述

统一管理所有 Git 仓库的认证信息，创建服务/模块时直接关联，避免重复输入认证信息。

#### 认证方式

| 认证类型 | 字段 | 说明 |
|----------|------|------|
| SSH Key | 认证名称 | 如 `github-personal-key` |
| | 私钥内容 | 直接粘贴私钥内容（RSA/ED25519） |
| | 私钥密码（Passphrase） | 可选，如私钥有密码保护 |
| HTTPS Token | 认证名称 | 如 `github-pat` |
| | Token 值 | Personal Access Token |
| | 适用域名 | 如 `github.com` / `gitlab.com`，可选 |

**安全要求**:
- 私钥内容和 Token 值在数据库中**加密存储**（AES-256）
- 列表页不展示敏感信息，只显示认证名称和类型
- 编辑时支持重新输入或保留原值

#### 认证列表

| 字段 | 类型 | 说明 |
|------|------|------|
| 认证名称 | String | 用户自定义 |
| 认证类型 | Enum | `SSH_KEY` / `HTTPS_TOKEN` |
| 适用域名 | String | 如 `github.com` |
| 关联模块数 | Number | 使用该认证的模块数量 |
| 创建时间 | DateTime | — |
| 操作 | — | 编辑 / 删除（被关联时禁止删除） |

---

### 3.6 🔧 服务管理（核心模块）

#### 3.6.1 服务列表

**页面布局**: 表格展示 + 顶部筛选

| 字段 | 类型 | 说明 |
|------|------|------|
| 服务名称 | String | 如 `order-service-prod` |
| 所属项目 | String | 关联项目名称 |
| 关联模块 | String | 关联模块名称 |
| 目标服务器 | String | 服务器主机名 |
| 环境 | Enum | `dev` / `test` / `staging` / `prod` |
| 端口 | Number | 服务监听端口 |
| 运行状态 | Enum | `运行中` / `已停止` / `部署中` / `异常` |
| 当前版本 | String | 当前部署的版本号 |
| 操作 | — | 查看详情 / 发版 / 重启 / 删除 |

**筛选条件**: 项目名称、环境类型、运行状态

**状态样式**:
- 🟢 运行中 — 绿色
- ⚫ 已停止 — 灰色
- 🔵 部署中 — 蓝色
- 🔴 异常 — 红色

#### 3.6.2 创建服务

**流程**: 分步表单

**Step 1: 选择项目和模块**

| 字段 | 类型 | 说明 |
|------|------|------|
| 所属项目 | 下拉选择 | 从已有项目中选择 |
| 关联模块 | 下拉选择 | 根据选择的项目，动态加载其模块列表 |

**Step 2: 填写服务基本信息**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| 服务名称 | String | ✅ | 唯一标识，如 `order-service-prod` |
| 目标服务器 | 下拉选择 | ✅ | 从服务器列表中选择 |
| 环境 | 下拉 | ✅ | 继承服务器环境类型 |
| 服务端口 | Number | ✅ | 如 `8080` |
| 部署基础路径 | String | ✅ | 如 `/opt/apps/order-service` |
| 运行时版本 | String | 可选 | 覆盖模块默认版本 |
| JVM 参数 | Textarea | 可选 | 如 `-Xms512m -Xmx1024m` |
| 环境变量 | Key-Value 列表 | 可选 | 如 `SPRING_PROFILES_ACTIVE=prod` |
| 健康检查路径 | String | 可选 | 如 `/actuator/health`，默认 `/` |
| 健康检查端口 | Number | 可选 | 默认同服务端口 |
| 启动等待时间(秒) | Number | 可选 | 健康检查前等待时间，默认 30 |

**Step 3: 确认创建**

- 展示服务配置摘要
- 系统自动创建部署目录结构（通过 SSH 远程执行 mkdir 命令）

**目录结构创建**:
```
/opt/apps/order-service/
├── versions/
├── build/
├── logs/
└── scripts/
```

#### 3.6.3 服务详情页

服务详情页是运维操作的核心页面，采用 **Tab 页签** 布局。

**顶部信息栏**:

```
┌─────────────────────────────────────────────────────────┐
│ 🔧 order-service-prod    所属: 订单系统 / order-service  │
│ 🟢 运行中  |  环境: prod  |  服务器: 192.168.1.100       │
│ 端口: 8080  |  当前版本: 20260412_002_v1.0.1            │
│                                                         │
│ [🚀 发版] [🔄 重启] [⏪ 回退] [⚙️ 配置] [⏰ 定时] [🗑️ 删除]│
└─────────────────────────────────────────────────────────┘
```

**Tab 页签**:

| Tab | 内容 |
|-----|------|
| 📜 发版记录 | 历史发版列表 |
| 📋 实时日志 | WebSocket 实时推送日志 |
| ⚙️ 配置管理 | 环境变量、JVM 参数、启动脚本编辑 |
| 📊 服务监控 | 进程状态、端口健康、响应时间 |
| ⏰ 定时发版 | 定时任务列表 |

---

##### Tab 1: 📜 发版记录

| 字段 | 类型 | 说明 |
|------|------|------|
| 版本号 | String | 如 `20260412_002_v1.0.1` |
| 分支/Tag | String | 如 `main` / `v1.0.1` |
| Commit ID | String | 如 `a1b2c3d`，前 7 位 |
| 操作人 | String | 执行发版的用户 |
| 开始时间 | DateTime | — |
| 结束时间 | DateTime | — |
| 耗时 | Duration | 如 `3m 25s` |
| 状态 | Enum | `成功` / `失败` / `回退` |
| 操作 | — | 查看详情（含完整日志） / 回退到此版本 |

**版本号命名规则**: `{日期}_{序号}_{Tag名}`
- 日期: `YYYYMMDD`，如 `20260412`
- 序号: 当日第几次发版，如 `001`、`002`
- Tag 名: Git Tag 名或分支名缩写，如 `v1.0.1`

**发版流程**（点击「🚀 发版」后弹出向导）:

```
Step 1: 选择发版目标
  └─ 分支/Tag 选择（从 Git 仓库远程拉取列表）
  └─ 可选：输入 Commit ID 指定版本

Step 2: 确认发版信息
  └─ 展示：服务名称、目标分支、当前版本、目标版本
  └─ 生产环境：二次确认弹窗

Step 3: 执行发版（实时进度展示）
  ┌─ ① 拉取代码        ████████████ 完成
  ┌─ ② 编译构建        ████████████ 完成
  ┌─ ③ 打包产物        ████████████ 完成
  ┌─ ④ 上传至服务器     ████████████ 完成
  ┌─ ⑤ 切换版本(软链)   ████████████ 完成
  ┌─ ⑥ 重启服务        ████████████ 完成
  └─ ⑦ 健康检查        ████████████ 完成 ✅

Step 4: 发版结果
  └─ 成功：绿色提示，显示耗时
  └─ 失败：红色提示，显示失败步骤和错误日志
           提供「一键回退」按钮
```

**发版详细步骤说明**:

| 步骤 | 操作 | 说明 |
|------|------|------|
| ① 拉取代码 | `git fetch` + `git checkout {分支}` | 在服务器上拉取指定分支 |
| ② 编译构建 | 执行模块配置的构建命令 | 如 `mvn clean package -pl xxx -am -DskipTests` |
| ③ 打包产物 | 将构建产物复制到 `versions/{版本号}/` | 同时复制 scripts 目录下的启停脚本 |
| ④ 上传至服务器 | SCP/SFTP 传输（如构建在远程服务器则跳过） | 本地构建后上传 |
| ⑤ 切换版本 | `ln -sfn versions/{版本号} current` | 更新软链 |
| ⑥ 重启服务 | 执行 `scripts/stop.sh` → `scripts/start.sh` | 停止旧进程，启动新版本 |
| ⑦ 健康检查 | HTTP GET `{健康检查路径}`，最多重试 N 次 | 返回 200 视为成功 |

---

##### Tab 2: 📋 实时日志

**功能**:

| 功能 | 说明 |
|------|------|
| 实时日志 | WebSocket 连接服务器执行 `tail -f logs/*.log`，实时推送到前端 |
| 日志级别过滤 | ALL / ERROR / WARN / INFO / DEBUG |
| 关键字搜索 | 输入关键字，高亮匹配行 |
| 自动滚动 | 默认开启，可暂停 |
| 日志行数限制 | 默认展示最近 1000 行，可调整 |
| 历史日志 | 切换到历史日志 Tab，选择日期范围查看 |
| 日志下载 | 支持下载日志文件 |

**页面布局**:
```
┌──────────────────────────────────────────────┐
│ [🔴 实时]  [📁 历史日志]                     │
│ 级别: [全部 ▼]  搜索: [_______________] [搜索]│
│ [自动滚动: ✓]  [清空]  [下载日志]             │
├──────────────────────────────────────────────┤
│ 2026-04-12 10:00:01 [INFO]  Starting service  │
│ 2026-04-12 10:00:02 [INFO]  Server started    │
│ 2026-04-12 10:00:05 [ERROR] Connection timeout│ ← 红色高亮
│ ...                                           │
└──────────────────────────────────────────────┘
```

---

##### Tab 3: ⚙️ 配置管理

可编辑的配置项：

| 配置项 | 说明 | 编辑后生效方式 |
|--------|------|---------------|
| 环境变量 | Key-Value 对，如 `SPRING_PROFILES_ACTIVE=prod` | 下次重启生效 |
| JVM 参数 | 如 `-Xms512m -Xmx1024m -XX:+UseG1GC` | 下次重启生效 |
| Node 运行参数 | Node.js 服务使用，如 `--max-old-space-size=4096` | 下次重启生效 |
| 启动脚本 | 自动生成的启停脚本可手动编辑 | 立即生效（下次重启使用新脚本） |

**启动脚本模板**:

`start.sh` 模板（Spring Boot JAR）:
```bash
#!/bin/bash
cd $(dirname $0)/../current

# 加载环境变量
if [ -f ../.env ]; then
    export $(cat ../.env | xargs)
fi

# 切换 JDK 版本
{如果配置了 jenv，执行 jenv shell {JDK版本}}

nohup java {JVM参数} -jar app.jar \
    --server.port={端口} \
    > ../logs/startup.log 2>&1 &

echo $! > ../pid

# 等待启动
sleep {启动等待时间}

# 健康检查
curl -s http://localhost:{端口}{健康检查路径} | grep -q "UP"
if [ $? -eq 0 ]; then
    echo "Service started successfully"
else
    echo "Service start failed"
    exit 1
fi
```

`stop.sh` 模板:
```bash
#!/bin/bash
if [ -f ../pid ]; then
    PID=$(cat ../pid)
    if kill -0 $PID 2>/dev/null; then
        kill $PID
        # 优雅关闭，等待 30 秒
        for i in $(seq 1 30); do
            kill -0 $PID 2>/dev/null || break
            sleep 1
        done
        # 如果还在运行，强制 kill
        kill -0 $PID 2>/dev/null && kill -9 $PID
        echo "Service stopped"
    else
        echo "Service not running"
    fi
    rm -f ../pid
else
    echo "PID file not found"
fi
```

---

##### Tab 4: 📊 服务监控

| 监控项 | 说明 |
|--------|------|
| 进程状态 | 进程 PID、CPU 使用率、内存使用率（通过 `ps` / `top` 命令获取） |
| 端口健康 | 端口是否可连接（通过 `curl` 或 `nc` 探测） |
| 响应时间 | HTTP 健康检查接口的响应时间（ms） |
| 运行时长 | 服务自上次启动以来的运行时长 |
| 磁盘使用 | 服务目录的磁盘占用 |
| 日志文件大小 | 日志目录总大小 |

**刷新频率**: 手动刷新 + 自动刷新（30s 间隔，可配置）

---

##### Tab 5: ⏰ 定时发版

| 字段 | 类型 | 说明 |
|------|------|------|
| 任务名称 | String | 如 `每日凌晨发布到测试环境` |
| 目标分支 | String | 如 `develop` |
| 触发方式 | Enum | `单次` / `Cron` |
| 执行时间 | DateTime | 单次发版的具体时间 |
| Cron 表达式 | String | 如 `0 2 * * *`，每日凌晨 2 点 |
| 失败回退策略 | Enum | `不回退` / `自动回退到上一版本` |
| 钉钉通知 | Boolean | 是否发送钉钉通知 |
| 状态 | Enum | `待执行` / `执行中` / `已完成` / `已失败` / `已取消` |
| 操作 | — | 编辑 / 删除 / 立即执行 / 取消 |

---

##### 服务操作：🔄 重启

**流程**:

1. 点击「🔄 重启」
2. 生产环境 → 弹出二次确认对话框
3. 确认后执行：
   - 执行 `stop.sh` 停止当前进程
   - 等待进程完全停止
   - 执行 `start.sh` 启动服务
   - 执行健康检查
4. 展示重启结果

##### 服务操作：⏪ 版本回退

**流程**:

1. 点击「⏪ 回退」
2. 弹出历史版本列表（最近 10 个版本）
3. 选择目标版本
4. 生产环境 → 二次确认
5. 确认后执行：
   - `ln -sfn versions/{目标版本} current` 切换软链
   - 执行 `stop.sh` → `start.sh` 重启
   - 健康检查
6. 记录回退操作到操作日志

##### 服务操作：🗑️ 删除

1. 仅当服务状态为「已停止」时允许删除
2. 删除确认弹窗（生产环境需二次确认）
3. 可选：是否同时删除服务器上的部署文件
4. 删除后服务记录保留在数据库中（软删除）

---

### 3.7 📜 操作日志

#### 功能描述

记录系统中所有关键操作的审计日志，用于问题追溯和责任认定。

#### 记录的操作类型

| 操作类型 | 说明 |
|----------|------|
| 服务发版 | 部署新版本 |
| 服务重启 | 手动/自动重启 |
| 版本回退 | 回退到历史版本 |
| 服务创建 | 新建服务实例 |
| 服务删除 | 删除服务实例 |
| 服务器添加 | 添加新服务器 |
| 服务器删除 | 删除服务器 |
| 项目创建 | 新建项目 |
| 项目删除 | 删除项目 |
| 模块添加 | 添加模块 |
| 模块删除 | 删除模块 |
| 配置修改 | 修改服务配置 |
| 定时任务创建 | 新建定时发版任务 |
| 定时任务取消 | 取消定时发版任务 |

#### 日志列表

| 字段 | 类型 | 说明 |
|------|------|------|
| 操作 ID | Long | 自增主键 |
| 操作人 | String | 执行操作的用户名 |
| 操作类型 | Enum | 见上表 |
| 操作对象 | String | 如 `order-service-prod` |
| 操作对象类型 | Enum | `SERVICE` / `SERVER` / `PROJECT` / `MODULE` |
| 操作详情 | Text | 操作的详细描述 |
| 环境 | Enum | 操作涉及的环境 |
| 操作结果 | Enum | `成功` / `失败` / `部分成功` |
| 错误信息 | Text | 失败时的错误信息 |
| 耗时 | Duration | 操作耗时 |
| IP 地址 | String | 操作发起 IP |
| 操作时间 | DateTime | — |

**搜索条件**: 操作人、操作类型、操作对象、环境、操作结果、时间范围

---

### 3.8 ⚙️ 系统设置

#### 3.8.1 钉钉通知配置

| 字段 | 类型 | 说明 |
|------|------|------|
| Webhook URL | String | 钉钉机器人 Webhook 地址 |
| 安全设置 | Enum | `自定义关键词` / `加签` / `IP 白名单` |
| 签名密钥 | String | 加签模式下使用 |
| 关键词 | String | 自定义关键词 |
| 通知场景 | 多选 | `发版成功` / `发版失败` / `服务异常` / `定时任务` |
| @通知人 | 多选 | 钉钉用户手机号列表 |
| 状态 | Enum | `已启用` / `已停用` |

#### 3.8.2 系统参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| 最大并发部署数 | 3 | 同时执行部署任务的最大数量 |
| 健康检查重试次数 | 5 | 健康检查最大重试次数 |
| 健康检查间隔(秒) | 10 | 每次重试间隔 |
| 日志保留天数 | 30 | 操作日志自动清理周期 |
| 构建产物保留数 | 10 | 每个服务保留的历史版本数量 |

---

## 4. 页面/交互说明

### 4.1 全局交互规范

#### 4.1.1 二次确认机制

涉及生产环境或数据删除的操作，必须弹出二次确认对话框：

```
┌──────────────────────────────────────────┐
│ ⚠️ 生产环境操作确认                       │
│                                          │
│ 操作类型: 发版部署                        │
│ 服务名称: order-service-prod              │
│ 目标分支: main                            │
│ 目标版本: v1.0.2                          │
│                                          │
│ 此操作将影响生产环境服务，请确认已做好     │
│ 充分测试并选择了正确的目标版本。           │
│                                          │
│ [ 取消 ]  [ 我已确认，执行操作 ]          │
└──────────────────────────────────────────┘
```

**触发二次确认的操作**:
- 生产环境发版
- 生产环境重启
- 生产环境回退
- 生产环境删除服务/服务器
- 删除项目（有关联服务时）
- 任何涉及 prod 环境的配置修改

#### 4.1.2 操作进度展示

所有异步操作（发版、重启等）通过以下方式展示进度：

1. **弹窗进度条**: 操作开始时弹出模态框，实时展示各步骤进度
2. **步骤指示器**: 显示当前步骤（如 `③ 编译构建`）和状态（进行中/完成/失败）
3. **实时日志**: 点击「查看日志」可查看该操作的详细日志输出
4. **操作完成通知**: 右上角弹出通知（成功/失败）
5. **钉钉通知**: 生产环境操作完成后发送钉钉通知

#### 4.1.3 错误处理

| 场景 | 处理方式 |
|------|----------|
| 部署失败 | 弹窗展示失败步骤和错误日志，提供「一键回退」按钮 |
| SSH 连接失败 | 提示连接错误，提供「重新连接」按钮 |
| 健康检查失败 | 展示最近日志片段，提示可能原因 |
| 网络超时 | 提示超时，提供「重试」按钮 |
| 并发冲突 | 同一服务正在部署时，提示「该服务正在部署中，请稍后再试」 |

---

### 4.2 关键页面说明

#### 4.2.1 发版部署流程（核心交互）

```
[服务详情页] → 点击「🚀 发版」
    ↓
[步骤 1: 选择版本]
    ├─ 分支下拉列表（从 Git 远程拉取）
    ├─ Tag 下拉列表（从 Git 远程拉取）
    ├─ 可手动输入 Commit ID
    └─ 显示最近 5 次提交记录（Commit message、作者、时间）
    ↓ [下一步]
[步骤 2: 确认信息]
    ├─ 服务名称、环境、当前版本、目标版本
    ├─ 预计影响说明（生产环境显示）
    └─ 生产环境：二次确认
    ↓ [开始部署]
[步骤 3: 部署执行]
    ├─ 7 步进度条（见 3.6.3）
    ├─ 实时日志输出区域（可展开/收起）
    ├─ 支持「取消部署」（非生产环境）
    └─ 失败时：自动停止，展示错误
    ↓
[步骤 4: 部署结果]
    ├─ 成功：绿色 ✅，显示耗时，显示新版本号
    ├─ 失败：红色 ❌，显示失败步骤和错误，提供「一键回退」
    └─ 记录到操作日志
```

#### 4.2.2 实时日志页

```
┌────────────────────────────────────────────────────┐
│ 服务: order-service-prod                            │
├────────────────────────────────────────────────────┤
│ [🔴 实时日志]  [📁 历史日志]                        │
│                                                     │
│ 级别过滤: [全部 ▼]  关键字: [NullPointerException] [🔍] │
│ [⬇️ 自动滚动: ON]  [暂停]  [清空]  [📥 下载]        │
├────────────────────────────────────────────────────┤
│ 2026-04-12 10:00:01.234 [INFO ] [main] c.o.OrderApplication  - Starting application │
│ 2026-04-12 10:00:02.456 [INFO ] [main] o.s.b.w.e.t.TomcatWebServer - Tomcat started  │
│ 2026-04-12 10:00:03.789 [INFO ] [main] c.o.OrderApplication  - Started in 2.5s      │
│ 2026-04-12 10:00:05.012 [ERROR] [http-nio-8080-exec-1] c.o.c.OrderController        │
│    java.lang.NullPointerException: Cannot invoke method on null object                │
│        at com.opspilot.controller.OrderController.get(OrderController.java:42)          │
│        at org.springframework.web.servlet...                                          │
│ 2026-04-12 10:00:06.345 [WARN ] [http-nio-8080-exec-2] c.o.f.RateLimitFilter - Rate  │
│ 2026-04-12 10:00:07.678 [INFO ] [scheduler-1] c.o.s.OrderSyncTask - Sync completed   │
│                                                ↑ 自动滚动到底部                      │
└────────────────────────────────────────────────────┘
```

#### 4.2.3 创建服务向导

```
┌────────────────────────────────────────────┐
│ 创建新服务                    [ × ]         │
├────────────────────────────────────────────┤
│                                            │
│ 步骤指示器: ① 选择模块  ② 基本信息  ③ 确认  │
│                                            │
│ ── Step 1: 选择项目和模块 ──                │
│                                            │
│ 所属项目: [订单系统 ▼]                      │
│ 关联模块: [order-service ▼]                 │
│                                            │
│ 模块信息预览:                               │
│   类型: Spring Boot JAR                     │
│   Git: git@github.com:xxx/order.git         │
│   JDK: 17                                   │
│   构建: mvn clean package -pl xxx -am ...   │
│                                            │
│         [ 取消 ]  [ 下一步 → ]              │
└────────────────────────────────────────────┘
```

---

## 5. 数据字段说明

### 5.1 核心表结构

#### 5.1.1 项目表 `t_project`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, Auto Increment | 主键 |
| name | VARCHAR(64) | NOT NULL, UNIQUE | 项目名称 |
| owner | VARCHAR(64) | NOT NULL | 负责人 |
| business_line | VARCHAR(64) | | 所属业务线 |
| tags | VARCHAR(256) | | 标签，逗号分隔，如 `电商,订单` |
| description | TEXT | | 项目描述 |
| deleted | TINYINT | DEFAULT 0 | 逻辑删除: 0-正常, 1-已删除 |
| create_time | DATETIME | NOT NULL | 创建时间 |
| update_time | DATETIME | NOT NULL | 更新时间 |

#### 5.1.2 模块表 `t_module`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, Auto Increment | 主键 |
| project_id | BIGINT | NOT NULL, FK | 所属项目 ID |
| name | VARCHAR(64) | NOT NULL | 模块名称 |
| module_type | VARCHAR(32) | NOT NULL | 类型: JAR/WAR/Vue/React/Node.js/Android/Flutter |
| git_repo_url | VARCHAR(512) | NOT NULL | Git 仓库地址 |
| git_branch | VARCHAR(64) | DEFAULT 'main' | 默认分支 |
| git_sub_path | VARCHAR(256) | | Monorepo 子路径 |
| maven_module_name | VARCHAR(64) | | Maven 模块名 |
| jdk_version | VARCHAR(16) | | JDK 版本: 8/11/17/21 |
| node_version | VARCHAR(16) | | Node.js 版本: 14/16/18/20 |
| build_command | VARCHAR(512) | NOT NULL | 构建命令 |
| build_artifact_path | VARCHAR(256) | NOT NULL | 构建产物路径 |
| git_cred_id | BIGINT | FK | 关联的 Git 认证 ID |
| deleted | TINYINT | DEFAULT 0 | 逻辑删除 |
| create_time | DATETIME | NOT NULL | — |
| update_time | DATETIME | NOT NULL | — |

#### 5.1.3 服务器表 `t_server`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, Auto Increment | 主键 |
| hostname | VARCHAR(64) | NOT NULL | 主机名 |
| ip_address | VARCHAR(64) | NOT NULL | IP 地址 |
| ssh_port | INT | DEFAULT 22 | SSH 端口 |
| ssh_username | VARCHAR(64) | NOT NULL | SSH 用户名 |
| env_type | VARCHAR(16) | NOT NULL | dev/test/staging/prod |
| status | VARCHAR(16) | DEFAULT 'unknown' | 在线/离线/探测中 |
| os_info | VARCHAR(128) | | OS 信息 |
| jdk_versions | VARCHAR(256) | | 已安装 JDK 列表，逗号分隔 |
| node_versions | VARCHAR(256) | | 已安装 Node 列表，逗号分隔 |
| has_nvm | TINYINT | DEFAULT 0 | 是否安装 NVM |
| has_jenv | TINYINT | DEFAULT 0 | 是否安装 jenv |
| has_docker | TINYINT | DEFAULT 0 | 是否安装 Docker |
| deleted | TINYINT | DEFAULT 0 | 逻辑删除 |
| create_time | DATETIME | NOT NULL | — |
| update_time | DATETIME | NOT NULL | — |

#### 5.1.4 Git 认证表 `t_git_credential`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, Auto Increment | 主键 |
| name | VARCHAR(64) | NOT NULL | 认证名称 |
| auth_type | VARCHAR(16) | NOT NULL | SSH_KEY / HTTPS_TOKEN |
| private_key_content | TEXT | | SSH 私钥内容（加密存储） |
| private_key_passphrase | VARCHAR(256) | | 私钥密码（加密存储） |
| token_value | VARCHAR(512) | | HTTPS Token（加密存储） |
| domain | VARCHAR(128) | | 适用域名，如 github.com |
| deleted | TINYINT | DEFAULT 0 | 逻辑删除 |
| create_time | DATETIME | NOT NULL | — |
| update_time | DATETIME | NOT NULL | — |

#### 5.1.5 服务实例表 `t_service`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, Auto Increment | 主键 |
| name | VARCHAR(64) | NOT NULL, UNIQUE | 服务名称 |
| project_id | BIGINT | NOT NULL, FK | 所属项目 ID |
| module_id | BIGINT | NOT NULL, FK | 关联模块 ID |
| server_id | BIGINT | NOT NULL, FK | 目标服务器 ID |
| env_type | VARCHAR(16) | NOT NULL | 环境类型 |
| port | INT | NOT NULL | 服务端口 |
| deploy_base_path | VARCHAR(256) | NOT NULL | 部署基础路径 |
| runtime_version | VARCHAR(16) | | 运行时版本（覆盖模块默认） |
| jvm_params | VARCHAR(512) | | JVM 参数 |
| env_variables | TEXT | | 环境变量，JSON 格式 |
| health_check_path | VARCHAR(128) | DEFAULT '/' | 健康检查路径 |
| health_check_port | INT | | 健康检查端口（默认同服务端口） |
| startup_wait_seconds | INT | DEFAULT 30 | 启动等待时间 |
| status | VARCHAR(16) | DEFAULT 'stopped' | running/stopped/deploying/error |
| current_version | VARCHAR(64) | | 当前部署版本号 |
| current_pid | INT | | 当前进程 PID |
| deleted | TINYINT | DEFAULT 0 | 逻辑删除 |
| create_time | DATETIME | NOT NULL | — |
| update_time | DATETIME | NOT NULL | — |

#### 5.1.6 发版记录表 `t_deploy_record`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, Auto Increment | 主键 |
| service_id | BIGINT | NOT NULL, FK | 服务 ID |
| version_no | VARCHAR(64) | NOT NULL | 版本号，如 20260412_001_v1.0.0 |
| git_branch | VARCHAR(64) | NOT NULL | 分支/Tag |
| commit_id | VARCHAR(64) | | Git Commit ID |
| operator | VARCHAR(64) | NOT NULL | 操作人 |
| status | VARCHAR(16) | NOT NULL | success/failed/rollback |
| start_time | DATETIME | NOT NULL | 开始时间 |
| end_time | DATETIME | | 结束时间 |
| duration_seconds | INT | | 耗时（秒） |
| error_message | TEXT | | 错误信息 |
| deploy_log | TEXT | | 部署日志摘要 |
| is_rollback | TINYINT | DEFAULT 0 | 是否回退版本 |
| create_time | DATETIME | NOT NULL | — |

#### 5.1.7 操作日志表 `t_operation_log`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, Auto Increment | 主键 |
| operator | VARCHAR(64) | NOT NULL | 操作人 |
| operation_type | VARCHAR(32) | NOT NULL | deploy/restart/rollback/create_service/... |
| target_name | VARCHAR(128) | NOT NULL | 操作对象名称 |
| target_type | VARCHAR(32) | NOT NULL | SERVICE/SERVER/PROJECT/MODULE |
| env_type | VARCHAR(16) | | 环境类型 |
| detail | TEXT | | 操作详情 |
| result | VARCHAR(16) | NOT NULL | success/failed/partial |
| error_message | TEXT | | 错误信息 |
| duration_seconds | INT | | 耗时 |
| client_ip | VARCHAR(64) | | 操作发起 IP |
| create_time | DATETIME | NOT NULL | 操作时间 |

#### 5.1.8 定时任务表 `t_schedule_task`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, Auto Increment | 主键 |
| service_id | BIGINT | NOT NULL, FK | 服务 ID |
| task_name | VARCHAR(64) | NOT NULL | 任务名称 |
| trigger_type | VARCHAR(16) | NOT NULL | ONCE / CRON |
| trigger_value | VARCHAR(64) | NOT NULL | 单次时间或 Cron 表达式 |
| git_branch | VARCHAR(64) | NOT NULL | 目标分支 |
| auto_rollback | TINYINT | DEFAULT 0 | 失败是否自动回退 |
| dingtalk_notify | TINYINT | DEFAULT 0 | 是否钉钉通知 |
| status | VARCHAR(16) | DEFAULT 'pending' | pending/running/completed/failed/cancelled |
| last_exec_time | DATETIME | | 上次执行时间 |
| last_exec_result | VARCHAR(16) | | 上次执行结果 |
| next_exec_time | DATETIME | | 下次执行时间 |
| deleted | TINYINT | DEFAULT 0 | 逻辑删除 |
| create_time | DATETIME | NOT NULL | — |
| update_time | DATETIME | NOT NULL | — |

#### 5.1.9 系统配置表 `t_system_config`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, Auto Increment | 主键 |
| config_key | VARCHAR(64) | NOT NULL, UNIQUE | 配置键 |
| config_value | TEXT | NOT NULL | 配置值 |
| config_type | VARCHAR(16) | NOT NULL | DINGTALK / SYSTEM |
| description | VARCHAR(256) | | 描述 |
| update_time | DATETIME | NOT NULL | — |

---

### 5.2 ER 关系图（简化）

```
t_project 1 ──── N t_module
t_module N ──── 1 t_git_credential
t_project 1 ──── N t_service
t_module 1 ──── N t_service
t_server 1 ──── N t_service
t_service 1 ──── N t_deploy_record
t_service 1 ──── N t_schedule_task
```

---

## 6. 非功能要求

### 6.1 性能要求

| 指标 | 要求 |
|------|------|
| 页面加载时间 | 首屏加载 < 2 秒（常规页面） |
| 接口响应时间 | 常规查询接口 < 500ms |
| 并发部署数 | 最多 3 个服务同时部署（可配置） |
| 日志推送延迟 | WebSocket 日志推送延迟 < 1 秒 |
| 发版时间 | 小型项目（纯 JAR）< 5 分钟（取决于编译时间） |
| 数据库连接池 | 最大连接数 20，最小空闲 5 |

### 6.2 安全要求

| 安全项 | 要求 |
|--------|------|
| 密码加密 | 用户密码使用 BCrypt 加密存储 |
| 敏感数据加密 | Git 私钥、Token 使用 AES-256 加密存储 |
| SSH 密码 | 添加服务器时输入的 SSH 密码不入库，用完即弃 |
| SQL 注入防护 | 使用 MyBatis Plus 参数化查询，禁止字符串拼接 SQL |
| XSS 防护 | 前端对用户输入内容进行转义 |
| CSRF 防护 | 前后端分离架构下使用 Token 认证 |
| SSH 密钥权限 | 服务器上的 SSH 私钥文件权限设为 600 |
| 操作审计 | 所有关键操作记录操作日志，不可删除 |

### 6.3 兼容性要求

| 兼容项 | 要求 |
|--------|------|
| 浏览器 | Chrome 90+、Edge 90+、Safari 14+ |
| 操作系统 | CentOS 7/8、Ubuntu 18.04/20.04/22.04 |
| JDK 版本 | 服务端运行 JDK 17+（Spring Boot 3 要求） |
| MySQL | MySQL 8.0+ |
| Redis | Redis 6.0+ |
| 移动端 | 响应式布局，支持平板端基本操作 |

### 6.4 可靠性要求

| 指标 | 要求 |
|------|------|
| 系统可用性 | 99.5%（非 7×24 强要求） |
| 数据备份 | 数据库每日自动备份 |
| 故障恢复 | 服务重启后可恢复运行 |
| 部署安全 | 发版失败时支持一键回退 |
| 日志保留 | 操作日志至少保留 30 天 |

### 6.5 可维护性要求

| 要求 | 说明 |
|------|------|
| 日志规范 | 后端统一日志格式，包含时间、级别、类名、线程 |
| 接口文档 | 使用 Swagger/Knife4j 自动生成 API 文档 |
| 代码规范 | 后端遵循阿里巴巴 Java 开发手册 |
| 错误码 | 统一错误码规范（模块码 + 序号） |

---

## 7. 验收标准

### 7.1 功能验收

#### 7.1.1 项目管理

- [ ] 可以创建、编辑、删除项目
- [ ] 可以为项目添加、编辑、删除模块
- [ ] 模块支持所有 7 种类型（JAR/WAR/Vue/React/Node.js/Android/Flutter）
- [ ] Maven 多模块项目的 `-pl -am` 参数正确拼装
- [ ] Monorepo 子路径正确解析
- [ ] 删除项目时校验无关联服务

#### 7.1.2 服务器管理

- [ ] 添加服务器时 SSH 密码正确建立互信
- [ ] SSH 密码不入库
- [ ] 环境自动探测信息正确
- [ ] 服务器按环境分组展示
- [ ] 生产服务器添加时有二次确认
- [ ] 服务器在线/离线状态正确更新

#### 7.1.3 Git 认证管理

- [ ] 可以创建 SSH Key 认证
- [ ] 可以创建 HTTPS Token 认证
- [ ] 私钥和 Token 加密存储
- [ ] 列表页不展示敏感信息
- [ ] 被关联的认证不可删除

#### 7.1.4 服务管理

- [ ] 可以创建服务实例，目录结构正确创建
- [ ] 发版部署流程完整（拉代码→构建→部署→健康检查）
- [ ] 发版进度实时展示
- [ ] 实时日志通过 WebSocket 推送
- [ ] 历史日志可搜索和下载
- [ ] 重启服务流程正确（停止→启动→健康检查）
- [ ] 版本回退功能正确（软链切换→重启→健康检查）
- [ ] 配置管理可编辑环境变量、JVM 参数、启动脚本
- [ ] 发版记录完整展示
- [ ] 定时发版支持单次和 Cron
- [ ] 定时任务失败自动回退策略生效
- [ ] 服务监控数据正确展示
- [ ] 生产环境操作有二次确认

#### 7.1.5 操作日志

- [ ] 所有关键操作均有记录
- [ ] 支持按操作人、类型、结果、时间查询
- [ ] 日志不可删除

#### 7.1.6 钉钉通知

- [ ] 发版成功/失败通知发送成功
- [ ] 定时任务结果通知发送成功
- [ ] 通知内容包含关键信息（服务名、版本、操作人、结果）

### 7.2 性能验收

- [ ] 页面首屏加载时间 < 2 秒
- [ ] 常规接口响应时间 < 500ms
- [ ] 支持同时部署 3 个服务
- [ ] WebSocket 日志推送延迟 < 1 秒

### 7.3 安全验收

- [ ] 密码 BCrypt 加密
- [ ] 敏感数据 AES 加密
- [ ] SSH 密码不入库
- [ ] 无 SQL 注入漏洞
- [ ] 无 XSS 漏洞
- [ ] 操作日志不可篡改

### 7.4 兼容性验收

- [ ] Chrome 90+ 正常显示和操作
- [ ] Edge 90+ 正常显示和操作
- [ ] 支持的 Linux 发行版上正常部署和运行
- [ ] 平板端基本操作正常

---

## 8. 附录

### 8.1 术语表

| 术语 | 说明 |
|------|------|
| 项目 (Project) | 业务系统级别的概念，如「订单系统」 |
| 模块 (Module) | 项目下的子模块，如「订单服务」、「管理后台前端」 |
| 服务 (Service) | 部署在特定服务器上的服务实例，是运维操作的基本单元 |
| 发版 (Deploy) | 从选择分支到部署完成的完整流程 |
| 回退 (Rollback) | 将服务恢复到历史版本 |
| 软链 (Symlink) | `current` 目录通过软链指向当前运行版本 |
| 健康检查 (Health Check) | 通过 HTTP 请求验证服务是否正常启动 |

### 8.2 版本号命名规则

格式：`{YYYYMMDD}_{序号}_{Tag/分支名}`

示例：
- `20260412_001_v1.0.0` — 2026年4月12日第1次发版，Tag 为 v1.0.0
- `20260412_002_hotfix-001` — 同一天第2次发版，来自 hotfix-001 分支

### 8.3 错误码规范

格式：`{模块码}{序号}`

| 模块 | 模块码 | 示例 |
|------|--------|------|
| 通用 | 10 | 10001-参数错误 |
| 认证 | 20 | 20001-用户名或密码错误 |
| 项目 | 30 | 30001-项目已存在 |
| 服务器 | 40 | 40001-服务器连接失败 |
| 服务 | 50 | 50001-服务正在部署中 |
| 部署 | 60 | 60001-拉取代码失败 |
| Git | 70 | 70001-Git 认证失败 |

### 8.4 钉钉通知模板

**发版成功**:
```
🟢 发版成功

服务: order-service-prod
环境: 生产
版本: 20260412_002_v1.0.1
分支: main
操作人: 小李
耗时: 3m 25s
```

**发版失败**:
```
🔴 发版失败

服务: order-service-prod
环境: 生产
版本: 20260412_003_v1.0.2
分支: main
操作人: 小李
失败步骤: 编译构建
错误: mvn build failed - Exit code 1
```

**服务异常**:
```
⚠️ 服务异常

服务: order-service-prod
环境: 生产
状态: 进程异常退出
PID: 12345
检测时间: 2026-04-12 14:30:00
```

---

> **文档结束**
> 
> 本文档为 OpsPilot V1.0 的完整产品需求文档，覆盖功能需求、交互设计、数据模型、非功能要求和验收标准。
> 
> 后续评审通过后，将进入技术架构设计阶段。
