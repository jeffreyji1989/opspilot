package com.opspilot.controller;

import com.opspilot.common.PageResult;
import com.opspilot.common.Result;
import com.opspilot.entity.DeployRecord;
import com.opspilot.entity.ServiceInstance;
import com.opspilot.service.DeployService;
import com.opspilot.service.OperationLogService;
import com.opspilot.service.ServiceInstanceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 服务实例控制器
 *
 * <p>提供服务实例的完整 CRUD 及运维操作 API，包括创建、编辑、删除、启动、停止、重启、
 * 版本回退、日志查看、进程信息查询、版本列表、配置查询和监控数据查询。</p>
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
    private final DeployService deployService;

    /**
     * 分页查询服务实例列表
     *
     * @param pageNum    页码
     * @param pageSize   每页大小
     * @param moduleId   模块 ID（可选）
     * @param serverId   服务器 ID（可选）
     * @param status     状态筛选（可选）
     * @return 分页结果
     */
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

    /**
     * 查询服务实例详情
     *
     * @param id 实例 ID
     * @return 实例详情
     */
    @GetMapping("/{id}")
    public Result<ServiceInstance> getById(@PathVariable Long id) {
        return Result.success(serviceInstanceService.getById(id));
    }

    /**
     * 创建服务实例（SSH 创建目录结构）
     *
     * @param instance 实例信息
     * @param request  HTTP 请求
     * @return 创建的实例
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
     * 编辑服务实例配置
     *
     * @param id       实例 ID
     * @param instance 更新的实例信息
     * @param request  HTTP 请求
     * @return 操作结果
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ServiceInstance instance, HttpServletRequest request) {
        instance.setId(id);
        serviceInstanceService.updateById(instance);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "UPDATE", "SERVICE", id,
                instance.getInstanceName(), "更新服务配置", "success", request.getRemoteAddr());
        return Result.success();
    }

    /**
     * 删除服务实例（有运行中进程时拒绝）
     *
     * @param id      实例 ID
     * @param request HTTP 请求
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        ServiceInstance inst = serviceInstanceService.getById(id);
        // 检查是否有运行中的进程
        if (inst != null && inst.getProcessStatus() != null && inst.getProcessStatus() == 1) {
            return Result.error("服务正在运行中，请先停止后再删除");
        }
        serviceInstanceService.removeById(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "DELETE", "SERVICE", id,
                inst != null ? inst.getInstanceName() : "", "删除服务实例", "success", request.getRemoteAddr());
        return Result.success();
    }

    /**
     * 启动服务
     *
     * @param id      实例 ID
     * @param request HTTP 请求
     * @return 启动输出
     */
    @PostMapping("/{id}/start")
    public Result<String> start(@PathVariable Long id, HttpServletRequest request) {
        String output = serviceInstanceService.startService(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "START", "SERVICE", id,
                "", "启动服务", "success", request.getRemoteAddr());
        return Result.success(output);
    }

    /**
     * 停止服务
     *
     * @param id      实例 ID
     * @param request HTTP 请求
     * @return 停止输出
     */
    @PostMapping("/{id}/stop")
    public Result<String> stop(@PathVariable Long id, HttpServletRequest request) {
        String output = serviceInstanceService.stopService(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "STOP", "SERVICE", id,
                "", "停止服务", "success", request.getRemoteAddr());
        return Result.success(output);
    }

    /**
     * 重启服务
     *
     * @param id      实例 ID
     * @param request HTTP 请求
     * @return 重启输出
     */
    @PostMapping("/{id}/restart")
    public Result<String> restart(@PathVariable Long id, HttpServletRequest request) {
        String output = serviceInstanceService.restartService(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "RESTART", "SERVICE", id,
                "", "重启服务", "success", request.getRemoteAddr());
        return Result.success(output);
    }

    /**
     * 查询服务历史日志
     *
     * @param id    实例 ID
     * @param lines 日志行数
     * @return 日志内容
     */
    @GetMapping("/{id}/logs")
    public Result<String> getLogs(@PathVariable Long id,
            @RequestParam(defaultValue = "200") int lines) {
        return Result.success(serviceInstanceService.getLogContent(id, lines));
    }

    /**
     * 查询服务进程信息
     *
     * @param id 实例 ID
     * @return 进程信息
     */
    @GetMapping("/{id}/process")
    public Result<String> getProcessInfo(@PathVariable Long id) {
        return Result.success(serviceInstanceService.getProcessInfo(id));
    }

    /**
     * 版本回退
     *
     * @param id      实例 ID
     * @param req     回退请求
     * @param request HTTP 请求
     * @return 操作结果
     */
    @PostMapping("/{id}/rollback")
    public Result<Void> rollback(@PathVariable Long id, @RequestBody RollbackRequest req, HttpServletRequest request) {
        serviceInstanceService.rollback(id, req.getTargetVersion());
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "service", "ROLLBACK", "SERVICE", id,
                req.getTargetVersion(), "版本回退到 " + req.getTargetVersion(), "success", request.getRemoteAddr());
        return Result.success();
    }

    /**
     * 查询服务实例的版本列表（发版历史）
     *
     * @param id 实例 ID
     * @return 版本列表
     */
    @GetMapping("/{id}/versions")
    public Result<List<DeployRecord>> getVersions(@PathVariable Long id) {
        return Result.success(serviceInstanceService.getVersions(id));
    }

    /**
     * 查询服务实例的配置信息
     *
     * @param id 实例 ID
     * @return 配置信息（环境变量、JVM 参数、启动脚本等）
     */
    @GetMapping("/{id}/config")
    public Result<Map<String, Object>> getConfig(@PathVariable Long id) {
        return Result.success(serviceInstanceService.getConfig(id));
    }

    /**
     * 查询服务实例的监控数据（CPU/内存/磁盘）
     *
     * @param id 实例 ID
     * @return 监控数据
     */
    @GetMapping("/{id}/monitor")
    public Result<Map<String, Object>> getMonitor(@PathVariable Long id) {
        return Result.success(serviceInstanceService.getMonitor(id));
    }

    @Data
    public static class RollbackRequest {
        private String targetVersion;
    }
}
