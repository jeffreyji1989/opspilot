package com.opspilot.controller;

import com.opspilot.common.Result;
import com.opspilot.entity.ServiceInstance;
import com.opspilot.service.ServiceInstanceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 服务配置管理控制器
 *
 * <p>提供服务实例的环境变量、JVM 参数、启动脚本的配置管理 API。</p>
 *
 * @author opspilot-team
 * @since 2026-04-14
 */
@RestController
@RequestMapping("/api/services/{instanceId}/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ServiceInstanceService serviceInstanceService;

    /**
     * 获取服务配置
     *
     * @param instanceId 实例ID
     * @return 配置信息
     */
    @GetMapping
    public Result<ServiceConfig> getConfig(@PathVariable Long instanceId) {
        ServiceInstance inst = serviceInstanceService.getById(instanceId);
        if (inst == null) {
            return Result.error(404, "服务实例不存在");
        }

        ServiceConfig config = new ServiceConfig();
        config.setJvmOptions(inst.getJvmOptions() != null ? inst.getJvmOptions() : "");
        config.setStartCommand(inst.getStartCommand() != null ? inst.getStartCommand() : "");
        config.setEnvironmentVariables(parseEnvVars(inst));
        config.setHealthCheckPath(inst.getHealthCheckPath() != null ? inst.getHealthCheckPath() : "/actuator/health");
        config.setHealthCheckPort(inst.getHealthCheckPort());

        return Result.success(config);
    }

    /**
     * 更新服务配置
     *
     * @param instanceId 实例ID
     * @param config     配置信息
     * @return 操作结果
     */
    @PostMapping
    public Result<Void> updateConfig(@PathVariable Long instanceId, @RequestBody ServiceConfig config) {
        ServiceInstance inst = serviceInstanceService.getById(instanceId);
        if (inst == null) {
            return Result.error(404, "服务实例不存在");
        }

        inst.setJvmOptions(config.getJvmOptions() != null ? config.getJvmOptions() : "");
        inst.setStartCommand(config.getStartCommand() != null ? config.getStartCommand() : "");
        inst.setHealthCheckPath(config.getHealthCheckPath() != null ? config.getHealthCheckPath() : "/actuator/health");
        inst.setHealthCheckPort(config.getHealthCheckPort());

        serviceInstanceService.updateById(inst);
        return Result.success();
    }

    /**
     * 解析环境变量（从 JVM 参数中提取 -D 开头的参数）
     */
    private List<EnvVar> parseEnvVars(ServiceInstance inst) {
        // 实际应用中可以从单独的配置表读取
        // 这里返回空列表，前端可手动添加
        return List.of();
    }

    @Data
    public static class ServiceConfig {
        /** JVM 启动参数 */
        private String jvmOptions;
        /** 自定义启动命令 */
        private String startCommand;
        /** 环境变量列表 */
        private List<EnvVar> environmentVariables;
        /** 健康检查路径 */
        private String healthCheckPath;
        /** 健康检查端口 */
        private Integer healthCheckPort;
    }

    @Data
    public static class EnvVar {
        /** 变量名 */
        private String key;
        /** 变量值 */
        private String value;
        /** 描述 */
        private String description;
    }
}
