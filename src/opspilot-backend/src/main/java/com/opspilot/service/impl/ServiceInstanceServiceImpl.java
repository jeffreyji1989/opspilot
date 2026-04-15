package com.opspilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.opspilot.common.BusinessException;
import com.opspilot.common.SshManager;
import com.opspilot.entity.DeployRecord;
import com.opspilot.entity.Module;
import com.opspilot.entity.Server;
import com.opspilot.entity.ServiceInstance;
import com.opspilot.mapper.DeployRecordMapper;
import com.opspilot.mapper.ModuleMapper;
import com.opspilot.mapper.ServerMapper;
import com.opspilot.mapper.ServiceInstanceMapper;
import com.opspilot.service.ServiceInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务实例服务实现类
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceInstanceServiceImpl extends ServiceImpl<ServiceInstanceMapper, ServiceInstance> implements ServiceInstanceService {

    private final ServerMapper serverMapper;
    private final ModuleMapper moduleMapper;
    private final DeployRecordMapper deployRecordMapper;
    private final SshManager sshManager;

    @Override
    public IPage<ServiceInstance> pageInstances(int pageNum, int pageSize, Long moduleId, Long serverId, Integer status) {
        LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
        if (moduleId != null) wrapper.eq(ServiceInstance::getModuleId, moduleId);
        if (serverId != null) wrapper.eq(ServiceInstance::getServerId, serverId);
        if (status != null) wrapper.eq(ServiceInstance::getStatus, status);
        wrapper.orderByDesc(ServiceInstance::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    @Transactional
    public void createInstanceWithDirSetup(ServiceInstance instance) {
        if (StringUtils.hasText(instance.getInstanceName())) {
            long count = count(new LambdaQueryWrapper<ServiceInstance>()
                    .eq(ServiceInstance::getInstanceName, instance.getInstanceName()));
            if (count > 0) throw new BusinessException("服务实例名称已存在");
        }
        // Validate module and server exist
        Module module = moduleMapper.selectById(instance.getModuleId());
        if (module == null) throw new BusinessException("模块不存在");
        Server server = serverMapper.selectById(instance.getServerId());
        if (server == null) throw new BusinessException("服务器不存在");

        if (instance.getRuntimeType() == null) instance.setRuntimeType("java");
        if (instance.getHealthCheckPort() == null) instance.setHealthCheckPort(instance.getListenPort());

        save(instance);

        // Create directory structure via SSH: deployPath/versions/current/releases + build + logs + scripts
        try {
            SSHClient ssh = sshManager.connect(server);
            try {
                String base = instance.getDeployPath();
                sshManager.executeCommand(ssh, String.format(
                        "mkdir -p %s/versions/current/releases %s/build %s/logs %s/scripts", base, base, base, base), 15);
                log.info("Deploy directories created at {}", base);
            } finally {
                ssh.close();
            }
        } catch (Exception e) {
            log.error("Failed to create deploy directories: {}", e.getMessage());
            throw new BusinessException("创建部署目录失败: " + e.getMessage());
        }
    }

    @Override
    public String startService(Long instanceId) {
        ServiceInstance inst = getByIdWithChecks(instanceId);
        Server server = serverMapper.selectById(inst.getServerId());
        String basePath = inst.getDeployPath();
        String script = String.format("cd %s/scripts && bash start.sh 2>&1", basePath);
        try {
            SSHClient ssh = sshManager.connect(server);
            String output = sshManager.executeCommand(ssh, script, 60);
            // Try to get PID
            String pidStr = sshManager.executeCommand(ssh, String.format("cat %s/pid 2>/dev/null", basePath), 5);
            if (StringUtils.hasText(pidStr)) {
                inst.setPid(Integer.parseInt(pidStr.trim()));
                inst.setProcessStatus(1);
            }
            updateById(inst);
            ssh.close();
            return output;
        } catch (Exception e) {
            throw new BusinessException("启动服务失败: " + e.getMessage());
        }
    }

    @Override
    public String stopService(Long instanceId) {
        ServiceInstance inst = getByIdWithChecks(instanceId);
        Server server = serverMapper.selectById(inst.getServerId());
        String basePath = inst.getDeployPath();
        try {
            SSHClient ssh = sshManager.connect(server);
            String output = sshManager.executeCommand(ssh,
                    String.format("cd %s/scripts && bash stop.sh 2>&1", basePath), 60);
            inst.setPid(null);
            inst.setProcessStatus(0);
            updateById(inst);
            ssh.close();
            return output;
        } catch (Exception e) {
            throw new BusinessException("停止服务失败: " + e.getMessage());
        }
    }

    @Override
    public String restartService(Long instanceId) {
        stopService(instanceId);
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return startService(instanceId);
    }

    @Override
    public String getLogContent(Long instanceId, int lines) {
        ServiceInstance inst = getByIdWithChecks(instanceId);
        Server server = serverMapper.selectById(inst.getServerId());
        String logPath = inst.getDeployPath() + "/logs";
        try {
            SSHClient ssh = sshManager.connect(server);
            String output = sshManager.executeCommand(ssh,
                    String.format("tail -n %d %s/*.log 2>/dev/null || echo 'No log files found'", lines, logPath), 10);
            ssh.close();
            return output;
        } catch (Exception e) {
            throw new BusinessException("获取日志失败: " + e.getMessage());
        }
    }

    @Override
    public void rollback(Long instanceId, String targetVersion) {
        ServiceInstance inst = getByIdWithChecks(instanceId);
        Server server = serverMapper.selectById(inst.getServerId());
        String basePath = inst.getDeployPath();
        try {
            SSHClient ssh = sshManager.connect(server);
            // Switch symlink
            sshManager.executeCommand(ssh, String.format(
                    "ln -sfn %s/versions/%s %s/current", basePath, targetVersion, basePath), 10);
            // Restart
            sshManager.executeCommand(ssh, String.format(
                    "cd %s/scripts && bash stop.sh 2>&1; sleep 2; bash start.sh 2>&1", basePath), 60);
            inst.setCurrentVersion(targetVersion);
            updateById(inst);
            ssh.close();
        } catch (Exception e) {
            throw new BusinessException("版本回退失败: " + e.getMessage());
        }
    }

    @Override
    public String getProcessInfo(Long instanceId) {
        ServiceInstance inst = getByIdWithChecks(instanceId);
        Server server = serverMapper.selectById(inst.getServerId());
        try {
            SSHClient ssh = sshManager.connect(server);
            String info = "";
            if (inst.getPid() != null) {
                info = sshManager.executeCommand(ssh,
                        String.format("ps -p %d -o pid,%%cpu,%%mem,etime,args 2>/dev/null", inst.getPid()), 5);
            }
            // Disk usage
            String disk = sshManager.executeCommand(ssh,
                    String.format("du -sh %s 2>/dev/null", inst.getDeployPath()), 5);
            info += "\n--- Disk Usage ---\n" + disk;
            ssh.close();
            return info;
        } catch (Exception e) {
            throw new BusinessException("获取进程信息失败: " + e.getMessage());
        }
    }

    @Override
    public List<DeployRecord> getVersions(Long instanceId) {
        LambdaQueryWrapper<DeployRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeployRecord::getInstanceId, instanceId)
                .ne(DeployRecord::getDeployType, "rollback")
                .orderByDesc(DeployRecord::getCreateTime)
                .last("LIMIT 50");
        return deployRecordMapper.selectList(wrapper);
    }

    @Override
    public Map<String, Object> getConfig(Long instanceId) {
        ServiceInstance inst = getByIdWithChecks(instanceId);
        Map<String, Object> config = new HashMap<>();
        config.put("instanceId", inst.getId());
        config.put("instanceName", inst.getInstanceName());
        config.put("deployPath", inst.getDeployPath());
        config.put("listenPort", inst.getListenPort());
        config.put("healthCheckPath", inst.getHealthCheckPath());
        config.put("healthCheckPort", inst.getHealthCheckPort());
        config.put("runtimeType", inst.getRuntimeType());
        config.put("runtimeVersion", inst.getRuntimeVersion());
        config.put("jvmOptions", inst.getJvmOptions());
        config.put("startCommand", inst.getStartCommand());
        config.put("currentVersion", inst.getCurrentVersion());
        return config;
    }

    @Override
    public Map<String, Object> getMonitor(Long instanceId) {
        ServiceInstance inst = getByIdWithChecks(instanceId);
        Server server = serverMapper.selectById(inst.getServerId());
        Map<String, Object> monitor = new HashMap<>();
        try {
            SSHClient ssh = sshManager.connect(server);

            // CPU usage
            String cpuCmd = "top -bn1 | grep 'Cpu(s)' | awk '{print $2}' 2>/dev/null || echo 'N/A'";
            String cpu = sshManager.executeCommand(ssh, cpuCmd, 5).trim();
            monitor.put("cpu", cpu);

            // Memory usage
            String memCmd = "free -m | awk 'NR==2{printf \"%.1f/%.0f MB (%.1f%%)\", $3,$2,$3*100/$2}' 2>/dev/null || echo 'N/A'";
            String mem = sshManager.executeCommand(ssh, memCmd, 5).trim();
            monitor.put("memory", mem);

            // Disk usage
            String diskCmd = String.format("df -h %s | awk 'NR==2{printf \"%s/%s (%s used)\", $3,$2,$5}' 2>/dev/null || echo 'N/A'",
                    inst.getDeployPath());
            String disk = sshManager.executeCommand(ssh, diskCmd, 5).trim();
            monitor.put("disk", disk);

            // Process CPU/MEM for the service
            if (inst.getPid() != null) {
                String procCmd = String.format("ps -p %d -o %%cpu,%%mem,rss 2>/dev/null | tail -1", inst.getPid());
                String procInfo = sshManager.executeCommand(ssh, procCmd, 5).trim();
                monitor.put("process", procInfo);
            }

            // System uptime
            String uptimeCmd = "uptime -p 2>/dev/null || uptime";
            String uptime = sshManager.executeCommand(ssh, uptimeCmd, 5).trim();
            monitor.put("systemUptime", uptime);

            ssh.close();
        } catch (Exception e) {
            log.error("获取监控数据失败: {}", e.getMessage());
            monitor.put("error", e.getMessage());
        }
        return monitor;
    }

    private ServiceInstance getByIdWithChecks(Long id) {
        ServiceInstance inst = getById(id);
        if (inst == null) throw new BusinessException("服务实例不存在");
        return inst;
    }
}
