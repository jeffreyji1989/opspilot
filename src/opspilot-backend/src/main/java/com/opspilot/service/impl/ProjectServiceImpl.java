package com.opspilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.opspilot.common.BusinessException;
import com.opspilot.entity.Module;
import com.opspilot.entity.Project;
import com.opspilot.entity.ServiceInstance;
import com.opspilot.mapper.ModuleMapper;
import com.opspilot.mapper.ProjectMapper;
import com.opspilot.mapper.ServiceInstanceMapper;
import com.opspilot.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {

    private final ModuleMapper moduleMapper;
    private final ServiceInstanceMapper serviceInstanceMapper;

    @Override
    public IPage<Project> pageProjects(int pageNum, int pageSize, String keyword, String businessLine) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Project::getProjectName, keyword)
                    .or()
                    .like(Project::getProjectCode, keyword);
        }
        if (StringUtils.hasText(businessLine)) {
            wrapper.eq(Project::getBusinessLine, businessLine);
        }
        wrapper.orderByDesc(Project::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public List<Module> getModulesByProjectId(Long projectId) {
        return moduleMapper.selectList(
                new LambdaQueryWrapper<Module>()
                        .eq(Module::getProjectId, projectId)
                        .orderByAsc(Module::getCreatedTime));
    }

    @Override
    @Transactional
    public Module addModule(Long projectId, Module module) {
        // Check project exists
        if (getById(projectId) == null) {
            throw new BusinessException("项目不存在");
        }
        // Check module name uniqueness within project
        long count = moduleMapper.selectCount(
                new LambdaQueryWrapper<Module>()
                        .eq(Module::getProjectId, projectId)
                        .eq(Module::getModuleName, module.getModuleName()));
        if (count > 0) {
            throw new BusinessException("该模块名称已存在");
        }
        module.setProjectId(projectId);
        if (!StringUtils.hasText(module.getBuildTool())) {
            module.setBuildTool("maven");
        }
        moduleMapper.insert(module);
        return module;
    }

    @Override
    @Transactional
    public void updateModule(Module module) {
        if (module.getId() == null || moduleMapper.selectById(module.getId()) == null) {
            throw new BusinessException("模块不存在");
        }
        moduleMapper.updateById(module);
    }

    @Override
    @Transactional
    public void deleteModule(Long moduleId) {
        Module module = moduleMapper.selectById(moduleId);
        if (module == null) {
            throw new BusinessException("模块不存在");
        }
        // Check if any service uses this module
        long count = serviceInstanceMapper.selectCount(
                new LambdaQueryWrapper<ServiceInstance>().eq(ServiceInstance::getModuleId, moduleId));
        if (count > 0) {
            throw new BusinessException("该模块下还有服务实例，无法删除");
        }
        moduleMapper.deleteById(moduleId);
    }

    @Override
    @Transactional
    public void deleteProject(Long id) {
        // Check associated modules
        long moduleCount = moduleMapper.selectCount(
                new LambdaQueryWrapper<Module>().eq(Module::getProjectId, id));
        if (moduleCount > 0) {
            throw new BusinessException(30002, "项目下存在模块，不允许删除");
        }
        // Check associated service instances (via module)
        List<Module> modules = moduleMapper.selectList(
                new LambdaQueryWrapper<Module>().eq(Module::getProjectId, id));
        if (!modules.isEmpty()) {
            List<Long> moduleIds = modules.stream().map(Module::getId).toList();
            long serviceCount = serviceInstanceMapper.selectCount(
                    new LambdaQueryWrapper<ServiceInstance>().in(ServiceInstance::getModuleId, moduleIds));
            if (serviceCount > 0) {
                throw new BusinessException(30003, "项目下存在服务实例，不允许删除");
            }
        }
        this.removeById(id);
    }
}
