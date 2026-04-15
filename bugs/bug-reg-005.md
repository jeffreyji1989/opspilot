# Bug-REG-005: Git 认证创建接口始终失败

| 属性 | 值 |
|------|------|
| **优先级** | P0（阻塞性） |
| **模块** | Git 认证管理 |
| **发现日期** | 2026-04-15 |
| **状态** | 未修复 |
| **GitHub Issue** | https://github.com/jeffreyji1989/opspilot/issues/5 |

## 复现步骤

1. 登录后调用 `POST /api/git-credentials`
2. 传入有效的认证数据
3. 观察返回结果

## 已尝试的参数组合

**组合 1 - 直接传入字段**: `{"credentialName":"test-ssh-key","credentialType":0,"encryptedData":"dGVzdA==","username":"test"}`

**组合 2 - 嵌套对象**: `{"credential":{"credentialName":"test","credentialType":0,"username":"test"},"encryptedData":"dGVzdA=="}`

**组合 3 - HTTPS Token**: `{"credentialName":"test-pat","credentialType":1,"username":"test","tokenValue":"ghp_test123","domain":"github.com"}`

## 期望结果

Git 认证创建成功

## 实际结果

所有组合均返回：`{"code": 500, "message": "凭证数据不能为空", "data": null}`

## 影响范围

- 无法添加新的 Git 认证
- 新模块无法关联 Git 仓库
