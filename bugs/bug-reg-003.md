# Bug-REG-003: 删除模块/服务器未校验关联服务实例

| 属性 | 值 |
|------|------|
| **优先级** | P0（阻塞性） |
| **模块** | 模块管理 / 服务器管理 |
| **发现日期** | 2026-04-15 |
| **状态** | 未修复 |
| **GitHub Issue** | https://github.com/jeffreyji1989/opspilot/issues/3 |

## 复现步骤

1. 确认模块 4 有关联的服务实例（服务实例 id=1 的 module_id=4）
2. 调用 `DELETE /api/modules/4`
3. 观察返回结果
4. 查询数据库：`SELECT deleted FROM t_module WHERE id=4`

## 期望结果

返回 400 错误：「模块下存在服务实例，不允许删除」

## 实际结果

```json
{"code": 200, "message": "success", "data": null}
```

数据库验证：模块 4 的 `deleted=1`，但服务实例 1 的 `module_id` 仍为 4，成为孤立引用。

## 数据完整性影响

| service_instance | module_id | server_id | module.deleted | server.deleted |
|-----------------|-----------|-----------|---------------|---------------|
| 印尼bpm-服务端 (id=1) | 4 | 1 | 1 | 1 |
| 联调测试实例 (id=2) | 1 | 1 | 0 | 1 |

服务实例引用的模块和服务器已被删除，数据一致性被破坏。

## 修复建议

在 ModuleService.deleteModule 和 ServerService.deleteServer 中添加关联服务实例校验：
```java
long serviceCount = serviceInstanceMapper.selectCount(
    new LambdaQueryWrapper<ServiceInstance>().eq(ServiceInstance::getModuleId, id));
if (serviceCount > 0) {
    throw new BusinessException(xxx, "模块下存在服务实例，不允许删除");
}
```
