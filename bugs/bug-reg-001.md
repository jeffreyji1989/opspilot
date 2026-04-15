# Bug-REG-001: /api/servers/by-env 路由冲突

| 属性 | 值 |
|------|------|
| **优先级** | P0（阻塞性） |
| **模块** | 服务器管理 |
| **发现日期** | 2026-04-15 |
| **状态** | 未修复 |
| **GitHub Issue** | https://github.com/jeffreyji1989/opspilot/issues/1 |

## 复现步骤

1. 登录后调用 `GET /api/servers/by-env`
2. 观察返回结果

## 期望结果

返回按环境分组的服务器列表

## 实际结果

```json
{
    "code": 500,
    "message": "系统内部错误: Failed to convert value of type 'java.lang.String' to required type 'java.lang.Long'; For input string: \"by-env\""
}
```

## 根因分析

Spring MVC 路由匹配顺序问题。`/api/servers/by-env` 路径被 `/api/servers/{id}` 路由捕获，尝试将 "by-env" 解析为 Long 类型 ID。

## 影响范围

- 仪表盘统计依赖此接口
- 服务器按环境分组页面不可用

## 修复建议

在 ServerController 中将 `@GetMapping("/by-env")` 定义在 `@GetMapping("/{id}")` 之前，或使用更明确的路径如 `@GetMapping("/grouped-by-env")`。
