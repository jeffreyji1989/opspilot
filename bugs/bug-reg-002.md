# Bug-REG-002: t_deploy_record 查询列名错误

| 属性 | 值 |
|------|------|
| **优先级** | P0（阻塞性） |
| **模块** | 发版部署 |
| **发现日期** | 2026-04-15 |
| **状态** | 未修复 |
| **GitHub Issue** | https://github.com/jeffreyji1989/opspilot/issues/2 |

## 复现步骤

1. 登录后调用 `GET /api/deploy/history/{moduleId}`
2. 观察返回结果

## 期望结果

返回该模块的部署历史记录列表

## 实际结果

```
Unknown column 'created_time' in 'order clause'
SQL: SELECT * FROM t_deploy_record WHERE module_id = ? ORDER BY created_time DESC
```

## 根因分析

DeployRecordMapper.java 中 `selectByModuleId` 方法使用 `ORDER BY created_time DESC`，但 `t_deploy_record` 表中的列名是 `create_time`（没有 'd'）。

## 影响范围

- 服务详情页发版记录 Tab 完全无法加载
- 所有部署历史查询均失败

## 修复建议

将 Mapper 中的 `created_time` 改为 `create_time`。
