# Bug-REG-004: 模块创建接口返回「不支持的模块类型」

| 属性 | 值 |
|------|------|
| **优先级** | P0（阻塞性） |
| **模块** | 模块管理 |
| **发现日期** | 2026-04-15 |
| **状态** | 未修复 |
| **GitHub Issue** | https://github.com/jeffreyji1989/opspilot/issues/4 |

## 复现步骤

1. 登录后调用 `POST /api/modules`
2. 传入 `{"projectId":17,"moduleName":"test-mod","moduleType":0,"repoUrl":"git@github.com:test/test.git","repoBranch":"main","buildCommand":"mvn clean package","artifactPath":"target/app.jar"}`
3. 观察返回结果

## 期望结果

模块创建成功

## 实际结果

```json
{"code": 500, "message": "不支持的模块类型: 0", "data": null}
```

所有 moduleType 值（0/1/2）均返回相同错误。

## 根因分析

模块创建逻辑中的 moduleType 映射不正确。数据库存储的是数字（0/1/2），但 Service 层可能期望字符串类型（"JAR"/"VUE"等），或者枚举映射未正确配置。

## 影响范围

- 无法创建任何新模块
- 所有 7 种模块类型均受影响

## 与 Bug-003 的关系

Bug-003 是构建模板接口的同样问题，两者根源相同。
