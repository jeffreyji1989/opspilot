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

/**
 * 服务实例控制器
 *
 * <p>提供服务实例的 CRUD、启动/停止/重启、日志查询、版本回退等 RESTful API。</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceInstanceController {

    private final ServiceInstanceService serviceInstanceService;
    private final OperationLogService operationLogService;

    /**
     * 分页查询服务实例列表
     *
     * @param pageNum    页码
     * @param pageSize   每页大小
     * @param moduleId   模块ID（可选筛选）
     * @param serverId   服务器ID（可选筛选）
     * @param status     状态（可选筛选）
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<ServiceInstance>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long moduleId,
            @RequestParam(required = false) Long serverId,
            @RequestParam(required = false) Integer processStatus) {
        return Result.success(PageResult.of(
                serviceInstanceService.pageInstances(pageNum, pageSize, moduleId, serverId, processStatus)));
    }

    /**
     * 获取服务实例详情
     *
     * @param id 实例ID
     * @return 服务实例信息
     */
    @GetMapping("/{id}")
    public Result<ServiceInstance> getById(@PathVariable Long id) {
        return Result.success(serviceInstanceService.getById(id));
    }

    /**
     * 创建服务实例（含目录初始化）
     *
     * @param instance 服务实例信息
     * @param request  HTTP 请求
     * @return 创建后的实例
     */
    @PostMapping
    public Result<ServiceInstance> create(@RequestBody ServiceInstance instance, HttpServletRequest request) {
        serviceInstanceService.createInstanceWithDirSetup(instance);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "CREATE", "SERVICE", instance.getId(),
                instance.getInstanceName(), "创建服务实例", "success", request.getRemoteAddr());
        return Result.success(instance);
    }

    /**
     * 编辑服务实例
     *
     * @param id       实例ID
     * @param instance 服务实例信息
     * @param request  HTTP 请求
     * @return 操作结果
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ServiceInstance instance, HttpServletRequest request) {
        instance.setId(id);
        ServiceInstance existing = serviceInstanceService.getById(id);
        if (existing == null) {
            return Result.error(404, "服务实例不存在");
        }
        serviceInstanceService.updateById(instance);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "UPDATE", "SERVICE", id,
                instance.getInstanceName(), "更新服务配置", "success", request.getRemoteAddr());
        return Result.success();
    }

    /**
     * 删除服务实例（有运行中进程时拒绝）
     *
     * @param id      实例ID
     * @param request HTTP 请求
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        ServiceInstance inst = serviceInstanceService.getById(id);
        if (inst == null) {
            return Result.error(404, "服务实例不存在");
        }
        // 有运行中进程时拒绝删除
        if (inst.getProcessStatus() != null && inst.getProcessStatus() == 1) {
            return Result.error(400, "服务正在运行中，请先停止服务再删除");
        }
        serviceInstanceService.removeById(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "DELETE", "SERVICE", id,
                inst.getInstanceName(), "删除服务实例", "success", request.getRemoteAddr());
        return Result.success();
    }

    /**
     * 启动服务
     *
     * @param id      实例ID
     * @param request HTTP 请求
     * @return 启动输出
     */
    @PostMapping("/{id}/start")
    public Result<String> start(@PathVariable Long id, HttpServletRequest request) {
        ServiceInstance inst = serviceInstanceService.getById(id);
        if (inst == null) {
            return Result.error(404, "服务实例不存在");
        }
        if (inst.getProcessStatus() != null && inst.getProcessStatus() == 1) {
            return Result.error(400, "服务已在运行中");
        }
        String output = serviceInstanceService.startService(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "START", "SERVICE", id,
                inst.getInstanceName(), "启动服务", "success", request.getRemoteAddr());
        return Result.success(output);
    }

    /**
     * 停止服务
     *
     * @param id      实例ID
     * @param request HTTP 请求
     * @return 停止输出
     */
    @PostMapping("/{id}/stop")
    public Result<String> stop(@PathVariable Long id, HttpServletRequest request) {
        ServiceInstance inst = serviceInstanceService.getById(id);
        if (inst == null) {
            return Result.error(404, "服务实例不存在");
        }
        if (inst.getProcessStatus() == null || inst.getProcessStatus() != 1) {
            return Result.error(400, "服务未运行");
        }
        String output = serviceInstanceService.stopService(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "STOP", "SERVICE", id,
                inst.getInstanceName(), "停止服务", "success", request.getRemoteAddr());
        return Result.success(output);
    }

    /**
     * 重启服务
     *
     * @param id      实例ID
     * @param request HTTP 请求
     * @return 重启输出
     */
    @PostMapping("/{id}/restart")
    public Result<String> restart(@PathVariable Long id, HttpServletRequest request) {
        ServiceInstance inst = serviceInstanceService.getById(id);
        if (inst == null) {
            return Result.error(404, "服务实例不存在");
        }
        if (inst.getProcessStatus() == null || inst.getProcessStatus() != 1) {
            return Result.error(400, "服务未运行，无法重启");
        }
        String output = serviceInstanceService.restartService(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "RESTART", "SERVICE", id,
                inst.getInstanceName(), "重启服务", "success", request.getRemoteAddr());
        return Result.success(output);
    }

    /**
     * 获取历史日志
     *
     * @param id     实例ID
     * @param lines  行数（默认200）
     * @return 日志内容
     */
    @GetMapping("/{id}/logs")
    public Result<String> getLogs(@PathVariable Long id,
            @RequestParam(defaultValue = "200") int lines) {
        return Result.success(serviceInstanceService.getLogContent(id, lines));
    }

    /**
     * 获取进程信息
     *
     * @param id 实例ID
     * @return 进程信息
     */
    @GetMapping("/{id}/process")
    public Result<String> getProcessInfo(@PathVariable Long id) {
        return Result.success(serviceInstanceService.getProcessInfo(id));
    }

    /**
     * 获取服务器监控数据（CPU/内存/磁盘）
     *
     * @param id 实例ID
     * @return 监控数据 JSON
     */
    @GetMapping("/{id}/monitor")
    public Result<String> getMonitorInfo(@PathVariable Long id) {
        return Result.success(serviceInstanceService.getMonitorInfo(id));
    }

    /**
     * 版本回退
     *
     * @param id      实例ID
     * @param req     回退请求（目标版本）
     * @param request HTTP 请求
     * @return 操作结果
     */
    @PostMapping("/{id}/rollback")
    public Result<Void> rollback(@PathVariable Long id, @RequestBody RollbackRequest req, HttpServletRequest request) {
        ServiceInstance inst = serviceInstanceService.getById(id);
        if (inst == null) {
            return Result.error(404, "服务实例不存在");
        }
        serviceInstanceService.rollback(id, req.getTargetVersion());
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "ROLLBACK", "SERVICE", id,
                req.getTargetVersion(), "版本回退到 " + req.getTargetVersion(), "success", request.getRemoteAddr());
        return Result.success();
    }

    /**
     * 获取历史版本列表
     *
     * @param id 实例ID
     * @return 版本列表
     */
    @GetMapping("/{id}/versions")
    public Result<java.util.List<String>> getVersions(@PathVariable Long id) {
        return Result.success(serviceInstanceService.getVersions(id));
    }

    @Data
    public static class RollbackRequest {
        /** 目标版本号 */
        private String targetVersion;
    }
}
