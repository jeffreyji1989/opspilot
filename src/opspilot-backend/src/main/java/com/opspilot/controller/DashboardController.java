package com.opspilot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opspilot.common.Result;
import com.opspilot.entity.Server;
import com.opspilot.entity.ServiceInstance;
import com.opspilot.entity.OperationLog;
import com.opspilot.entity.Project;
import com.opspilot.mapper.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 仪表盘 Controller
 *
 * <p>提供全局统计卡片、环境分布、服务健康、最近操作记录等聚合查询。</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ProjectMapper projectMapper;
    private final ServerMapper serverMapper;
    private final ServiceInstanceMapper instanceMapper;
    private final OperationLogMapper operationLogMapper;

    /**
     * 仪表盘统计卡片
     */
    @GetMapping("/stats")
    public Result<DashboardStats> stats() {
        DashboardStats stats = new DashboardStats();

        // 项目总数
        stats.setProjectCount(projectMapper.selectCount(null));

        // 服务器总数
        stats.setServerCount(serverMapper.selectCount(null));

        // 服务实例总数
        stats.setInstanceCount(instanceMapper.selectCount(null));

        // 运行中 / 已停止
        stats.setRunningCount(instanceMapper.selectCount(
                new LambdaQueryWrapper<ServiceInstance>().eq(ServiceInstance::getProcessStatus, 1)));
        stats.setStoppedCount(instanceMapper.selectCount(
                new LambdaQueryWrapper<ServiceInstance>().eq(ServiceInstance::getProcessStatus, 0)));

        // 今日操作数
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        stats.setTodayOps(operationLogMapper.selectCount(
                new LambdaQueryWrapper<OperationLog>().ge(OperationLog::getCreateTime, todayStart)));

        // 环境分布
        Map<String, Long> envDist = new LinkedHashMap<>();
        envDist.put("dev", 0L);
        envDist.put("test", 0L);
        envDist.put("staging", 0L);
        envDist.put("prod", 0L);
        List<Server> servers = serverMapper.selectList(null);
        for (Server s : servers) {
            String env = switch (s.getEnvType()) {
                case 0 -> "dev";
                case 1 -> "test";
                case 2 -> "staging";
                case 3 -> "prod";
                default -> "unknown";
            };
            envDist.merge(env, 1L, Long::sum);
        }
        stats.setEnvDistribution(envDist);

        // 服务健康状态
        List<ServiceInstance> allInstances = instanceMapper.selectList(null);
        List<ServiceHealthItem> healthItems = allInstances.stream().map(inst -> {
            ServiceHealthItem item = new ServiceHealthItem();
            item.setId(inst.getId());
            item.setInstanceName(inst.getInstanceName());
            item.setStatus(inst.getProcessStatus());
            return item;
        }).collect(Collectors.toList());
        stats.setServiceHealth(healthItems);

        // 最近 10 条操作记录
        stats.setRecentOps(operationLogMapper.selectList(
                new LambdaQueryWrapper<OperationLog>()
                        .orderByDesc(OperationLog::getCreateTime)
                        .last("LIMIT 10")));

        return Result.success(stats);
    }

    /**
     * 仪表盘统计数据
     */
    @Data
    public static class DashboardStats {
        private Long projectCount;
        private Long serverCount;
        private Long instanceCount;
        private Long runningCount;
        private Long stoppedCount;
        private Long todayOps;
        private Map<String, Long> envDistribution;
        private List<ServiceHealthItem> serviceHealth;
        private List<OperationLog> recentOps;
    }

    /**
     * 服务健康状态项
     */
    @Data
    public static class ServiceHealthItem {
        private Long id;
        private String instanceName;
        /** 0-停止, 1-运行中, 2-启动中, 3-停止中 */
        private Integer status;
    }
}
