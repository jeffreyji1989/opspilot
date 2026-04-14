package com.opspilot.controller;

import com.opspilot.common.PageResult;
import com.opspilot.common.Result;
import com.opspilot.entity.Module;
import com.opspilot.entity.Project;
import com.opspilot.service.ModuleService;
import com.opspilot.service.OperationLogService;
import com.opspilot.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目管理 Controller
 *
 * <p>提供项目的增删改查接口，项目编码唯一性校验，删除前校验关联模块和服务。</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ModuleService moduleService;
    private final OperationLogService operationLogService;

    /**
     * 分页查询项目列表
     */
    @GetMapping
    public Result<PageResult<Project>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String businessLine) {
        return Result.success(PageResult.of(projectService.pageProjects(pageNum, pageSize, keyword, businessLine)));
    }

    /**
     * 获取项目详情
     */
    @GetMapping("/{id}")
    public Result<Project> getById(@PathVariable Long id) {
        Project project = projectService.getById(id);
        if (project == null) {
            return Result.error(404, "项目不存在");
        }
        return Result.success(project);
    }

    /**
     * 获取项目下所有模块
     */
    @GetMapping("/{id}/modules")
    public Result<List<Module>> getModules(@PathVariable Long id) {
        return Result.success(moduleService.listByProjectId(id));
    }

    /**
     * 创建项目（校验 projectCode 唯一性）
     */
    @PostMapping
    public Result<Project> create(@RequestBody Project project, HttpServletRequest request) {
        if (projectService.existsByProjectCode(project.getProjectCode(), null)) {
            return Result.error(30001, "项目编码已存在");
        }
        Long userId = (Long) request.getAttribute("userId");
        project.setOwnerId(userId);
        projectService.save(project);
        operationLogService.logOperation(userId, "project", "CREATE", "PROJECT", project.getId(),
                project.getProjectName(), "创建项目", "success", request.getRemoteAddr());
        return Result.success(project);
    }

    /**
     * 编辑项目
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Project project, HttpServletRequest request) {
        if (project.getProjectCode() != null && projectService.existsByProjectCode(project.getProjectCode(), id)) {
            return Result.error(30001, "项目编码已存在");
        }
        project.setId(id);
        projectService.updateById(project);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "project", "UPDATE", "PROJECT", id,
                project.getProjectName(), "更新项目", "success", request.getRemoteAddr());
        return Result.success();
    }

    /**
     * 删除项目（校验无关联模块和服务）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Project project = projectService.getById(id);
        if (project == null) {
            return Result.error(404, "项目不存在");
        }
        projectService.deleteProject(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "project", "DELETE", "PROJECT", id,
                project.getProjectName(), "删除项目", "success", request.getRemoteAddr());
        return Result.success();
    }

    /**
     * 项目详情响应（含模块数）
     */
    public static class ProjectDetailResponse {
        private Project project;
        private int moduleCount;

        public ProjectDetailResponse(Project project, int moduleCount) {
            this.project = project;
            this.moduleCount = moduleCount;
        }

        public Project getProject() { return project; }
        public void setProject(Project project) { this.project = project; }
        public int getModuleCount() { return moduleCount; }
        public void setModuleCount(int moduleCount) { this.moduleCount = moduleCount; }
    }
}
