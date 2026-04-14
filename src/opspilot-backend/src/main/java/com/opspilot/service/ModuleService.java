package com.opspilot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.opspilot.entity.Module;

import java.util.List;

/**
 * 模块服务接口
 *
 * @author opspilot-team
 * @since 2026-04-14
 */
public interface ModuleService extends IService<Module> {

    /**
     * 根据项目 ID 获取模块列表
     */
    List<Module> listByProjectId(Long projectId);

    /**
     * 创建模块（自动拼装构建命令）
     */
    Module createModule(Module module);

    /**
     * 更新模块
     */
    void updateModule(Module module);

    /**
     * 删除模块（校验无关联服务实例）
     */
    void deleteModule(Long id);

    /**
     * 根据模块类型获取构建命令模板
     *
     * @param moduleType JAR/WAR/Vue/React/Node.js/Android/Flutter
     * @return {buildCommand, artifactPath, buildTool, runtimeType}
     */
    java.util.Map<String, String> getBuildTemplate(String moduleType);
}
