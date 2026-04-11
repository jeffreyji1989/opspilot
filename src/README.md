# OpsPilot 运维管理系统

> 轻量级、开箱即用的运维管理系统，用于管理多项目、多服务器、多环境的发版部署和运行监控。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.2 + MyBatis Plus 3.5 + MySQL 8 + Redis |
| 前端 | Vue 3 + Element Plus + Pinia |
| SSH | SSHJ（支持 Ed25519 密钥） |
| 定时任务 | Quartz（动态增删改） |
| 加密 | AES-256-GCM（Git 认证存储） |
| 认证 | JWT |

## 项目结构

```
projects/opspilot/src/
├── opspilot-backend/          # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/opspilot/
│       │   ├── OpsPilotApplication.java
│       │   ├── config/            # 配置类
│       │   ├── controller/        # 控制器
│       │   ├── service/           # 业务层
│       │   ├── service/impl/      # 业务实现
│       │   ├── mapper/            # MyBatis Mapper
│       │   ├── entity/            # 实体类
│       │   ├── dto/               # DTO
│       │   ├── common/            # 通用工具
│       │   ├── task/              # Quartz 定时任务
│       │   └── websocket/         # WebSocket
│       └── resources/
│           ├── application.yml
│           └── sql/init.sql
├── opspilot-frontend/         # Vue 3 前端
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── main.js
│       ├── App.vue
│       ├── views/             # 页面
│       ├── router/            # 路由
│       ├── store/             # Pinia
│       ├── api/               # API 请求
│       └── styles/
```

## 快速开始

### 1. 数据库初始化

```bash
mysql -u root -p < src/main/resources/sql/init.sql
```

### 2. 后端启动

```bash
cd opspilot-backend
# 修改 application.yml 中的数据库和 Redis 配置
mvn spring-boot:run
```

### 3. 前端启动

```bash
cd opspilot-frontend
npm install
npm run dev
```

### 4. 默认账号

- 用户名：`admin`
- 密码：`admin123`

## 核心功能

| 模块 | 功能 |
|------|------|
| 用户认证 | JWT 登录，BCrypt 密码加密 |
| 项目管理 | 项目 CRUD + 模块管理 |
| 服务器管理 | 服务器 CRUD + SSH 互信自动建立 + 环境探测 |
| Git 认证 | SSH Key / PAT Token 管理，AES-256-GCM 加密 |
| 服务管理 | 服务实例创建、启动、停止、重启 |
| 发版部署 | 拉代码 → 构建 → 部署 → 健康检查 |
| 实时日志 | WebSocket 推送 `tail -f` 日志 |
| 版本回退 | 软链切换 + 重启 |
| 定时发版 | Quartz 动态任务管理 |
| 操作日志 | 全量操作审计 |
| 仪表盘 | 全局统计 + 服务健康 |

## 部署目录结构

服务部署后，服务器上会创建以下目录结构：

```
/opt/apps/service-name/
├── versions/          # 各版本产物
│   └── 20260412_001_main/
│       └── app.jar
├── current            # 软链 → versions/xxx
├── build/             # Git 代码仓库
├── logs/              # 运行日志
├── scripts/           # 启停脚本
│   ├── start.sh
│   └── stop.sh
└── pid                # 进程 PID
```

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `OPSPILOT_AES_KEY` | AES 加密密钥（至少 16 字符） | `DefaultAesKey16Ch` |

## 二次开发

- 后端接口：`/api/**`
- 前端代理：Vite 已配置 `/api` 代理到 `localhost:8080`
- WebSocket：`ws://localhost:3000/api/ws/log/{instanceId}`
