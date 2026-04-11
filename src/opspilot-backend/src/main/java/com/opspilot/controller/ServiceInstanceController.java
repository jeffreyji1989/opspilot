package com.opspilot.controller;

import com.opspilot.common.PageResult;
import com.opspilot.common.Result;
import com.opspilot.entity.ServiceInstance;
import com.opspilot.service.OperationLogService;
import com.opspilot.service.ServiceInstanceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceInstanceController {

    private final ServiceInstanceService serviceInstanceService;
    private final OperationLogService operationLogService;

    @GetMapping
    public Result<PageResult<ServiceInstance>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long moduleId,
            @RequestParam(required = false) Long serverId,
            @RequestParam(required = false) Integer status) {
        return Result.success(PageResult.of(
                serviceInstanceService.pageInstances(pageNum, pageSize, moduleId, serverId, status)));
    }

    @GetMapping("/{id}")
    public Result<ServiceInstance> getById(@PathVariable Long id) {
        return Result.success(serviceInstanceService.getById(id));
    }

    @PostMapping
    public Result<ServiceInstance> create(@RequestBody ServiceInstance instance, HttpServletRequest request) {
        serviceInstanceService.createInstanceWithDirSetup(instance);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "CREATE", "SERVICE", instance.getId(),
                instance.getInstanceName(), "创建服务实例", "success", request.getRemoteAddr());
        return Result.success(instance);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ServiceInstance instance, HttpServletRequest request) {
        instance.setId(id);
        serviceInstanceService.updateById(instance);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "UPDATE", "SERVICE", id,
                instance.getInstanceName(), "更新服务配置", "success", request.getRemoteAddr());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        ServiceInstance inst = serviceInstanceService.getById(id);
        serviceInstanceService.removeById(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "DELETE", "SERVICE", id,
                inst != null ? inst.getInstanceName() : "", "删除服务实例", "success", request.getRemoteAddr());
        return Result.success();
    }

    @PostMapping("/{id}/start")
    public Result<String> start(@PathVariable Long id, HttpServletRequest request) {
        String output = serviceInstanceService.startService(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "START", "SERVICE", id,
                "", "启动服务", "success", request.getRemoteAddr());
        return Result.success(output);
    }

    @PostMapping("/{id}/stop")
    public Result<String> stop(@PathVariable Long id, HttpServletRequest request) {
        String output = serviceInstanceService.stopService(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "STOP", "SERVICE", id,
                "", "停止服务", "success", request.getRemoteAddr());
        return Result.success(output);
    }

    @PostMapping("/{id}/restart")
    public Result<String> restart(@PathVariable Long id, HttpServletRequest request) {
        String output = serviceInstanceService.restartService(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "RESTART", "SERVICE", id,
                "", "重启服务", "success", request.getRemoteAddr());
        return Result.success(output);
    }

    @GetMapping("/{id}/logs")
    public Result<String> getLogs(@PathVariable Long id,
            @RequestParam(defaultValue = "200") int lines) {
        return Result.success(serviceInstanceService.getLogContent(id, lines));
    }

    @GetMapping("/{id}/process")
    public Result<String> getProcessInfo(@PathVariable Long id) {
        return Result.success(serviceInstanceService.getProcessInfo(id));
    }

    @PostMapping("/{id}/rollback")
    public Result<Void> rollback(@PathVariable Long id, @RequestBody RollbackRequest req, HttpServletRequest request) {
        serviceInstanceService.rollback(id, req.getTargetVersion());
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "ROLLBACK", "SERVICE", id,
                req.getTargetVersion(), "版本回退到 " + req.getTargetVersion(), "success", request.getRemoteAddr());
        return Result.success();
    }

    @Data
    public static class RollbackRequest {
        private String targetVersion;
    }
}
