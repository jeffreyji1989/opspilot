package com.opspilot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opspilot.common.Result;
import com.opspilot.entity.Server;
import com.opspilot.entity.ServiceInstance;
import com.opspilot.entity.OperationLog;
import com.opspilot.mapper.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ProjectMapper projectMapper;
    private final ServerMapper serverMapper;
    private final ServiceInstanceMapper instanceMapper;
    private final OperationLogMapper operationLogMapper;

    @GetMapping("/stats")
    public Result<DashboardStats> stats() {
        DashboardStats stats = new DashboardStats();
        stats.setProjectCount(projectMapper.selectCount(null));
        stats.setServerCount(serverMapper.selectCount(null));
        stats.setInstanceCount(instanceMapper.selectCount(null));
        stats.setRunningCount(instanceMapper.selectCount(
                new LambdaQueryWrapper<ServiceInstance>().eq(ServiceInstance::getProcessStatus, 1)));
        stats.setStoppedCount(instanceMapper.selectCount(
                new LambdaQueryWrapper<ServiceInstance>().eq(ServiceInstance::getProcessStatus, 0)));

        // Today's operations
        java.time.LocalDateTime todayStart = java.time.LocalDate.now().atStartOfDay();
        stats.setTodayOps(operationLogMapper.selectCount(
                new LambdaQueryWrapper<OperationLog>().ge(OperationLog::getCreateTime, todayStart)));

        // Server env distribution
        Map<String, Long> envDist = new HashMap<>();
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

        // Recent operations
        stats.setRecentOps(operationLogMapper.selectList(
                new LambdaQueryWrapper<OperationLog>()
                        .orderByDesc(OperationLog::getCreateTime)
                        .last("LIMIT 10")));

        // Service health
        stats.setServiceHealth(instanceMapper.selectList(
                new LambdaQueryWrapper<ServiceInstance>().select(ServiceInstance::getInstanceName, ServiceInstance::getProcessStatus)));

        return Result.success(stats);
    }

    @Data
    public static class DashboardStats {
        private Long projectCount;
        private Long serverCount;
        private Long instanceCount;
        private Long runningCount;
        private Long stoppedCount;
        private Long todayOps;
        private Map<String, Long> envDistribution;
        private List<OperationLog> recentOps;
        private List<ServiceInstance> serviceHealth;
    }
}
