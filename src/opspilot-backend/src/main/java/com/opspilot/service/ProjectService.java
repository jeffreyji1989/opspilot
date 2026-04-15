package com.opspilot.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.opspilot.entity.Project;

/**
 * 项目服务接口
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
public interface ProjectService extends IService<Project> {
    /**
     * 分页查询项目
     */
    IPage<Project> pageProjects(int pageNum, int pageSize, String keyword, String businessLine);

    /**
     * 校验项目编码唯一性
     *
     * @param projectCode 项目编码
     * @param excludeId   排除的项目 ID（编辑时排除自身）
     * @return true 如果已存在
     */
    boolean existsByProjectCode(String projectCode, Long excludeId);

    /**
     * 删除项目前校验关联模块和服务实例
     */
    void deleteProject(Long id);
}
