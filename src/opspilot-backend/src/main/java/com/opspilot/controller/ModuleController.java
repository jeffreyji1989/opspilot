package com.opspilot.controller;

import com.opspilot.common.Result;
import com.opspilot.entity.Module;
import com.opspilot.service.ModuleService;
import com.opspilot.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 模块管理 Controller
 *
 * <p>提供模块的增删改查、构建命令模板查询等接口。</p>
 *
 * @author opspilot-team
 * @since 2026-04-14
 */
@RestController
@RequestMapping("/api/modules")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleService moduleService;
    private final OperationLogService operationLogService;

    @GetMapping("/project/{projectId}")
    public Result<List<Module>> listByProject(@PathVariable Long projectId) {
        return Result.success(moduleService.listByProjectId(projectId));
    }

    @GetMapping("/{id}")
    public Result<Module> getById(@PathVariable Long id) {
        Module module = moduleService.getById(id);
        if (module == null) {
            return Result.error(404, "模块不存在");
        }
        return Result.success(module);
    }

    @PostMapping
    public Result<Module> create(@RequestBody Module module, HttpServletRequest request) {
        Module saved = moduleService.createModule(module);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "module", "CREATE", "MODULE", saved.getId(),
                saved.getModuleName(), "添加模块", "success", request.getRemoteAddr());
        return Result.success(saved);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Module module, HttpServletRequest request) {
        module.setId(id);
        moduleService.updateModule(module);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "module", "UPDATE", "MODULE", id,
                module.getModuleName(), "更新模块", "success", request.getRemoteAddr());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        moduleService.deleteModule(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "module", "DELETE", "MODULE", id,
                "", "删除模块", "success", request.getRemoteAddr());
        return Result.success();
    }

    @GetMapping("/build-template/{moduleType}")
    public Result<Map<String, String>> getBuildTemplate(@PathVariable String moduleType) {
        return Result.success(moduleService.getBuildTemplate(moduleType));
    }
}
