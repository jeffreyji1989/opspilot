package com.opspilot.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.opspilot.entity.Module;
import com.opspilot.entity.Project;

import java.util.List;

public interface ProjectService extends IService<Project> {
    IPage<Project> pageProjects(int pageNum, int pageSize, String keyword, String businessLine);
    List<Module> getModulesByProjectId(Long projectId);
    Module addModule(Long projectId, Module module);
    void updateModule(Module module);
    void deleteModule(Long moduleId);
    /** 删除项目前校验关联模块和服务实例 */
    void deleteProject(Long id);
}
