-- OpsPilot v1.0 数据库迁移脚本
-- 执行日期: 2026-04-14
-- 说明: 补充全栈 A + 全栈 B 新增实体字段

USE opspilot;

-- t_module 新增字段（全栈 B 服务管理增强）
ALTER TABLE t_module ADD COLUMN IF NOT EXISTS node_version VARCHAR(50) DEFAULT NULL COMMENT 'Node.js 版本';
ALTER TABLE t_module ADD COLUMN IF NOT EXISTS git_cred_id BIGINT DEFAULT NULL COMMENT '关联Git凭证ID';
ALTER TABLE t_module ADD COLUMN IF NOT EXISTS type VARCHAR(50) DEFAULT NULL COMMENT '类型别名';

-- t_git_credential 新增字段（全栈 A Git 认证增强）
ALTER TABLE t_git_credential ADD COLUMN IF NOT EXISTS domain VARCHAR(255) DEFAULT NULL COMMENT '适用域名';

-- t_operation_log 新增字段（全栈 B 操作日志增强）
ALTER TABLE t_operation_log ADD COLUMN IF NOT EXISTS result VARCHAR(32) DEFAULT 'success' COMMENT '操作结果';
