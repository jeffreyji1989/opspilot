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

/**
 * 项目服务实现
 *
 * <p>提供项目的分页查询、唯一性校验、删除校验等功能。</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
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
    public boolean existsByProjectCode(String projectCode, Long excludeId) {
        if (!StringUtils.hasText(projectCode)) {
            return false;
        }
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
                .eq(Project::getProjectCode, projectCode);
        if (excludeId != null) {
            wrapper.ne(Project::getId, excludeId);
        }
        return count(wrapper) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(Long id) {
        long moduleCount = moduleMapper.selectCount(
                new LambdaQueryWrapper<Module>().eq(Module::getProjectId, id));
        if (moduleCount > 0) {
            throw new BusinessException(30002, "项目下存在模块，不允许删除");
        }
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
        removeById(id);
    }
}
