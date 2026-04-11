package com.opspilot.controller;

import com.opspilot.common.PageResult;
import com.opspilot.common.Result;
import com.opspilot.entity.Module;
import com.opspilot.entity.Project;
import com.opspilot.service.OperationLogService;
import com.opspilot.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final OperationLogService operationLogService;

    @GetMapping
    public Result<PageResult<Project>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String businessLine) {
        return Result.success(PageResult.of(projectService.pageProjects(pageNum, pageSize, keyword, businessLine)));
    }

    @GetMapping("/{id}")
    public Result<Project> getById(@PathVariable Long id) {
        return Result.success(projectService.getById(id));
    }

    @PostMapping
    public Result<Project> create(@RequestBody Project project, HttpServletRequest request) {
        projectService.save(project);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "project", "CREATE", "PROJECT", project.getId(),
                project.getProjectName(), "创建项目", "success", request.getRemoteAddr());
        return Result.success(project);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Project project, HttpServletRequest request) {
        project.setId(id);
        projectService.updateById(project);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "project", "UPDATE", "PROJECT", id,
                project.getProjectName(), "更新项目", "success", request.getRemoteAddr());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Project project = projectService.getById(id);
        projectService.deleteProject(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "project", "DELETE", "PROJECT", id,
                project != null ? project.getProjectName() : "", "删除项目", "success", request.getRemoteAddr());
        return Result.success();
    }

    @GetMapping("/{id}/modules")
    public Result<List<Module>> getModules(@PathVariable Long id) {
        return Result.success(projectService.getModulesByProjectId(id));
    }

    @PostMapping("/{id}/modules")
    public Result<Module> addModule(@PathVariable Long id, @RequestBody Module module, HttpServletRequest request) {
        Module saved = projectService.addModule(id, module);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "module", "CREATE", "MODULE", saved.getId(),
                saved.getModuleName(), "添加模块", "success", request.getRemoteAddr());
        return Result.success(saved);
    }

    @PutMapping("/modules/{moduleId}")
    public Result<Void> updateModule(@PathVariable Long moduleId, @RequestBody Module module, HttpServletRequest request) {
        module.setId(moduleId);
        projectService.updateModule(module);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "module", "UPDATE", "MODULE", moduleId,
                module.getModuleName(), "更新模块", "success", request.getRemoteAddr());
        return Result.success();
    }

    @DeleteMapping("/modules/{moduleId}")
    public Result<Void> deleteModule(@PathVariable Long moduleId, HttpServletRequest request) {
        projectService.deleteModule(moduleId);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "module", "DELETE", "MODULE", moduleId,
                "", "删除模块", "success", request.getRemoteAddr());
        return Result.success();
    }
}
