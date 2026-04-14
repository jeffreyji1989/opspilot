# OpsPilot GitHub 协作工作流

> 本文档定义虚拟开发团队与 GitHub 功能的完整集成方案

---

## 1. Issues — 任务与 Bug 追踪

### 1.1 Issue 类型

| 类型 | 前缀 | 创建者 | 示例 |
|------|------|--------|------|
| 🐛 Bug | `bug:` | QA 工程师 | `bug: 服务器列表 500 错误` |
| ✨ Feature | `feat:` | 产品经理 | `feat: 支持 Docker 容器部署` |
| 📝 Docs | `docs:` | 任何角色 | `docs: 补充数据库设计文档` |
| ♻️ Refactor | `refactor:` | 架构师 | `refactor: DeployServiceImpl 拆分` |
| 🚀 Release | `release:` | 队长 | `release: v1.0.0 发布准备` |

### 1.2 Issue 模板

每个 Issue 必须包含：
```markdown
## 描述
<问题或需求的详细描述>

## 优先级
- [ ] P0 — 阻塞性，必须立即修复
- [ ] P1 — 重要，本版本内解决
- [ ] P2 — 一般，可延后

## 关联模块
- 后端 / 前端 / 数据库 / 文档

## 指派给
@full-stack-developer / @qa-engineer / 等

## 验收标准
- [ ] 标准 1
- [ ] 标准 2
```

### 1.3 谁创建 Issue

| 角色 | 创建时机 |
|------|---------|
| QA | 发现 Bug → 提交 `bug:` Issue |
| PM | 新需求/用户故事 → 提交 `feat:` Issue |
| 架构师 | 技术债务 → 提交 `refactor:` Issue |
| 队长 | 版本发布 → 提交 `release:` Issue |

### 1.4 生命周期

```
Open → In Progress (开发中) → Review (待评审) → Done (已关闭)
       ↓                    ↓
     Reopened           Rejected (打回)
```

---

## 2. Feature Branch — 功能分支

### 2.1 分支命名规则

```
<类型>/<issue编号>-<简短描述>

示例:
feature/12-docker-deploy
bugfix/15-server-list-500
refactor/20-deploy-service-split
hotfix/25-jwt-expiry-fix
```

### 2.2 分支策略

```
master ──────────────────────────────────→ 生产
  │
  ├── test ──────────────────────────────→ 集成测试
  │     │
  │     ├── feature/12-docker-deploy ────→ 开发中
  │     ├── bugfix/15-server-list-500 ──→ 修复中
  │     └── feature/18-monitor-dashboard → 开发中
  │
  └── v1.0-ops-v2-20260414 ────────────→ 旧功能分支（可清理）
```

### 2.3 分支工作流

```
0. 架构师拆分任务包 → 直接指派给全栈 A/B/C
   (在 task-assignment.md 中写明每个任务包的负责人)

1. 全栈从 test 创建功能分支
   git checkout -b feature/12-docker-deploy test

2. 开发完成，推送到 GitHub
   git push origin feature/12-docker-deploy

3. 创建 Pull Request → test
   gh pr create --base test --title "feat: 支持 Docker 容器部署 (#12)"

4. 架构师 Review PR
   gh pr review --approve

5. 合并到 test
   gh pr merge --squash --delete-branch

6. 关闭关联 Issue
   gh issue close 12
```

---

## 3. Pull Request — 代码评审

### 3.1 PR 模板

```markdown
## 📋 描述
<本次 PR 做了什么>

## 🔗 关联 Issue
Closes #12

## ✅ 自检清单
- [ ] 代码编译通过
- [ ] 单元测试通过
- [ ] 无 TODO/FIXME 残留
- [ ] 符合阿里开发手册
- [ ] Javadoc 完整

## 🧪 测试方法
1. 启动后端
2. 调用 xxx 接口
3. 验证返回 xxx

## 📸 截图（前端变更时）
<截图>
```

### 3.2 PR 评审流程

```
全栈提交 PR
    ↓
架构师 Review（通过/请求变更/评论）
    ↓
全栈修改（如需要）
    ↓
架构师 Approve
    ↓
合并到 test → 自动删除功能分支
    ↓
QA 在 test 环境验证 → 关闭 Issue
```

### 3.3 评审规则

| 规则 | 说明 |
|------|------|
| 必须 1 人 Approve | 架构师必须 approve 才能合并 |
| 必须关联 Issue | PR 描述中必须 `Closes #xx` |
| 必须编译通过 | CI 检查（后续可加 GitHub Actions） |
| Squash Merge | 保持 test 分支历史整洁 |
| 删除源分支 | 合并后自动删除 feature 分支 |

---

## 4. 完整工作流示例

### 场景：开发「Docker 容器部署」功能

```
📋 PM 创建 Issue #12: feat: 支持 Docker 容器部署
   - 优先级: P1
   - 描述: 用户可在服务器上通过 Docker 部署服务
   - 指派给: @full-stack-developer-A

🏗️ 架构师评审 PRD 可行性 → 通过

💻 全栈 A 从 test 创建分支
   git checkout -b feature/12-docker-deploy test

💻 开发完成，推送并创建 PR
   git push origin feature/12-docker-deploy
   gh pr create --base test --title "feat: 支持 Docker 容器部署 (#12)"

🏗️ 架构师 Review PR
   - 检查代码质量 → 通过
   - 检查数据库变更 → 合理
   - Approve PR
   gh pr review --approve

🔄 合并到 test
   gh pr merge --squash --delete-branch
   → Issue #12 自动关闭

🧪 QA 在 test 环境验证
   - 执行测试用例 → 通过
   - 评论: ✅ 验证通过

📦 版本发布时
   gh pr create --base master --title "release: v1.1.0"
   → 架构师 Approve → 合并到 master
   → git tag -a v1.1.0 -m "支持 Docker 容器部署"
```

---

## 5. 虚拟团队角色映射

| GitHub 功能 | PM | 架构师 | 全栈 A | 全栈 B | 全栈 C | QA | 队长 |
|------------|----|--------|--------|--------|--------|----|------|
| 创建 Issue | ✨ feat | ♻️ refactor | 🐛 bug | 🐛 bug | 🐛 bug | 🐛 bug | 🚀 release |
| 拆分任务 | - | ✅ **直接派发** | 接收任务 | 接收任务 | 接收任务 | - | - |
| 创建分支 | - | - | ✅ | ✅ | ✅ | - | - |
| 提交代码 | - | - | ✅ | ✅ | ✅ | - | - |
| 创建 PR | - | - | ✅ | ✅ | ✅ | - | - |
| Review PR | - | ✅ approve | comment | comment | comment | - | - |
| 合并 PR | - | ✅ | - | - | - | - | - |
| 验证 Issue | - | - | - | - | - | ✅ | - |
| 关闭 Issue | - | - | - | - | - | ✅ | - |
| 进度汇报 | - | - | - | - | - | - | ✅ **负责** |

### 队长与技术职责边界

| 职责 | 负责人 | 说明 |
|------|--------|------|
| 任务拆分与派发 | **架构师** | 最懂技术细节，拆和派是同一个人 |
| 代码评审 | **架构师** | 批准 PR、打回修改 |
| 技术决策 | **架构师** | 技术方案、架构选型 |
| 进度汇报 | **队长** | 向 Jeffrey 同步项目状态 |
| 异常处理 | **队长** | 多个角色意见冲突时拍板 |
| 非技术协调 | **队长** | 排期、资源、沟通 |
| 发版决策 | **队长** | 何时发布、是否继续 |

---

## 6. 自动化（未来规划）

### GitHub Actions

```yaml
name: CI
on: [pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Build Backend
        run: cd opspilot-backend && mvn clean compile
      - name: Build Frontend
        run: cd opspilot-frontend && npm run build
```

### Issue 自动分配
- `bug:` → 自动指派给 QA + 全栈
- `feat:` → 自动指派给全栈
- `release:` → 通知队长

### PR 自动检查
- 编译检查
- 代码格式检查（阿里规范）
- 单元测试覆盖率 ≥ 80%

---

## 7. gh-cli 命令速查

```bash
# 查看 Issues
gh issue list --repo jeffreyji1989/opspilot

# 创建 Issue
gh issue create --repo jeffreyji1989/opspilot \
  --title "bug: 服务器列表 500" \
  --body "## 描述\n..." \
  --label "bug,p0"

# 创建 PR
gh pr create --base test \
  --title "fix: 修复服务器列表 500 (#15)" \
  --body "Closes #15"

# Review PR
gh pr view 15 --repo jeffreyji1989/opspilot
gh pr review 15 --approve --repo jeffreyji1989/opspilot

# 合并 PR
gh pr merge 15 --squash --delete-branch --repo jeffreyji1989/opspilot

# 查看分支
gh branch list --repo jeffreyji1989/opspilot

# 创建标签
gh release create v1.0.0 --title "v1.0.0 首发" --generate-notes
```

---

## 8. 目录约定

```
.github/
├── ISSUE_TEMPLATE/
│   ├── bug-report.md        # Bug 报告模板
│   ├── feature-request.md   # 功能需求模板
│   └── task.md              # 一般任务模板
├── PULL_REQUEST_TEMPLATE.md # PR 模板
└── workflows/
    └── ci.yml               # CI 工作流
```
