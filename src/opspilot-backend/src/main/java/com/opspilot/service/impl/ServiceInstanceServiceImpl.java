package com.opspilot.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.opspilot.common.BusinessException;
import com.opspilot.common.SshManager;
import com.opspilot.entity.Module;
import com.opspilot.entity.Server;
import com.opspilot.entity.ServiceInstance;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务实例服务实现类
 *
 * <p>提供服务实例的 CRUD、启动/停止/重启、日志查询、版本回退、监控数据采集等功能。</p>
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
    private final SshManager sshManager;

    @Override
    public IPage<ServiceInstance> pageInstances(int pageNum, int pageSize, Long moduleId, Long serverId, Integer processStatus) {
        LambdaQueryWrapper<ServiceInstance> wrapper = new LambdaQueryWrapper<>();
        if (moduleId != null) wrapper.eq(ServiceInstance::getModuleId, moduleId);
        if (serverId != null) wrapper.eq(ServiceInstance::getServerId, serverId);
        if (processStatus != null) wrapper.eq(ServiceInstance::getProcessStatus, processStatus);
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
        if (instance.getProcessStatus() == null) instance.setProcessStatus(0);
        if (instance.getStatus() == null) instance.setStatus(1);

        save(instance);

        // Create directory structure via SSH
        try {
            SSHClient ssh = sshManager.connect(server);
            try {
                String base = instance.getDeployPath();
                sshManager.executeCommand(ssh, String.format(
                        "mkdir -p %s/versions %s/current %s/releases %s/build %s/logs %s/scripts",
                        base, base, base, base, base, base), 15);
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
        if (server == null) throw new BusinessException("服务器不存在");
        String basePath = inst.getDeployPath();
        String script = String.format("cd %s/scripts && bash start.sh 2>&1", basePath);
        try {
            SSHClient ssh = sshManager.connect(server);
            try {
                String output = sshManager.executeCommand(ssh, script, 60);
                // Try to get PID
                Thread.sleep(3000);
                String pidStr = sshManager.executeCommand(ssh,
                        String.format("pgrep -f 'java.*%s' | head -1", basePath), 5);
                if (StringUtils.hasText(pidStr)) {
                    inst.setPid(Integer.parseInt(pidStr.trim()));
                    inst.setProcessStatus(1);
                } else {
                    inst.setProcessStatus(1);
                }
                updateById(inst);
                return output;
            } finally {
                ssh.close();
            }
        } catch (Exception e) {
            throw new BusinessException("启动服务失败: " + e.getMessage());
        }
    }

    @Override
    public String stopService(Long instanceId) {
        ServiceInstance inst = getByIdWithChecks(instanceId);
        Server server = serverMapper.selectById(inst.getServerId());
        if (server == null) throw new BusinessException("服务器不存在");
        String basePath = inst.getDeployPath();
        try {
            SSHClient ssh = sshManager.connect(server);
            try {
                String output = sshManager.executeCommand(ssh,
                        String.format("cd %s/scripts && bash stop.sh 2>&1 || pkill -f 'java.*%s' 2>/dev/null; echo 'stopped'", basePath, basePath), 60);
                inst.setPid(null);
                inst.setProcessStatus(0);
                updateById(inst);
                return output;
            } finally {
                ssh.close();
            }
        } catch (Exception e) {
            throw new BusinessException("停止服务失败: " + e.getMessage());
        }
    }

    @Override
    public String restartService(Long instanceId) {
        log.info("开始重启服务, instanceId={}", instanceId);
        stopService(instanceId);
        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        String output = startService(instanceId);
        log.info("服务重启完成, instanceId={}", instanceId);
        return output;
    }

    @Override
    public String getLogContent(Long instanceId, int lines) {
        ServiceInstance inst = getByIdWithChecks(instanceId);
        Server server = serverMapper.selectById(inst.getServerId());
        if (server == null) throw new BusinessException("服务器不存在");
        String logPath = inst.getDeployPath() + "/logs";
        try {
            SSHClient ssh = sshManager.connect(server);
            try {
                String output = sshManager.executeCommand(ssh,
                        String.format("tail -n %d %s/*.log 2>/dev/null || echo 'No log files found'", lines, logPath), 10);
                return output;
            } finally {
                ssh.close();
            }
        } catch (Exception e) {
            throw new BusinessException("获取日志失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void rollback(Long instanceId, String targetVersion) {
        log.info("开始版本回退, instanceId={}, targetVersion={}", instanceId, targetVersion);
        ServiceInstance inst = getByIdWithChecks(instanceId);
        Server server = serverMapper.selectById(inst.getServerId());
        if (server == null) throw new BusinessException("服务器不存在");
        String basePath = inst.getDeployPath();
        try {
            SSHClient ssh = sshManager.connect(server);
            try {
                // Check target version directory exists
                String checkCmd = String.format("test -d %s/versions/%s && echo 'exists' || echo 'not_exists'", basePath, targetVersion);
                String checkResult = sshManager.executeCommand(ssh, checkCmd, 10);
                if (!"exists".equals(checkResult.trim())) {
                    throw new BusinessException("目标版本目录不存在: " + targetVersion);
                }

                // Switch symlink
                sshManager.executeCommand(ssh, String.format(
                        "ln -sfn %s/versions/%s %s/current", basePath, targetVersion, basePath), 10);

                // Restart
                sshManager.executeCommand(ssh, String.format(
                        "pkill -f 'java.*%s' 2>/dev/null; sleep 3", basePath), 30);
                String startCmd;
                if (StringUtils.hasText(inst.getStartCommand())) {
                    startCmd = String.format("cd %s/current && %s &", basePath, inst.getStartCommand());
                } else if (StringUtils.hasText(inst.getJvmOptions())) {
                    startCmd = String.format("cd %s/current && nohup java %s -jar *.jar > app.log 2>&1 &", basePath, inst.getJvmOptions());
                } else {
                    startCmd = String.format("cd %s/current && nohup java -jar *.jar > app.log 2>&1 &", basePath);
                }
                sshManager.executeCommand(ssh, startCmd, 30);
                Thread.sleep(5000);

                inst.setCurrentVersion(targetVersion);
                updateById(inst);
                log.info("版本回退成功, instanceId={}, version={}", instanceId, targetVersion);
            } finally {
                ssh.close();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("版本回退失败: " + e.getMessage());
        }
    }

    @Override
    public String getProcessInfo(Long instanceId) {
        ServiceInstance inst = getByIdWithChecks(instanceId);
        Server server = serverMapper.selectById(inst.getServerId());
        if (server == null) throw new BusinessException("服务器不存在");
        try {
            SSHClient ssh = sshManager.connect(server);
            try {
                String info = "";
                if (inst.getPid() != null) {
                    info = sshManager.executeCommand(ssh,
                            String.format("ps -p %d -o pid,%%cpu,%%mem,etime,args 2>/dev/null || echo 'Process not found'", inst.getPid()), 5);
                }
                // Disk usage
                String disk = sshManager.executeCommand(ssh,
                        String.format("du -sh %s 2>/dev/null", inst.getDeployPath()), 5);
                info += "\n--- Disk Usage ---\n" + disk;
                return info;
            } finally {
                ssh.close();
            }
        } catch (Exception e) {
            throw new BusinessException("获取进程信息失败: " + e.getMessage());
        }
    }

    @Override
    public String getMonitorInfo(Long instanceId) {
        ServiceInstance inst = getByIdWithChecks(instanceId);
        Server server = serverMapper.selectById(inst.getServerId());
        if (server == null) throw new BusinessException("服务器不存在");
        try {
            SSHClient ssh = sshManager.connect(server);
            try {
                Map<String, Object> monitor = new HashMap<>();

                // CPU usage
                String cpuCmd = "top -bn1 | grep 'Cpu(s)' | awk '{print $2}' | cut -d'%' -f1 2>/dev/null || echo '0'";
                String cpuResult = sshManager.executeCommand(ssh, cpuCmd, 10).trim();
                try {
                    monitor.put("cpuUsage", Double.parseDouble(cpuResult));
                } catch (NumberFormatException e) {
                    monitor.put("cpuUsage", 0.0);
                }

                // Memory usage
                String memCmd = "free | grep Mem | awk '{printf \"%.1f\", $3/$2 * 100}' 2>/dev/null || echo '0'";
                String memResult = sshManager.executeCommand(ssh, memCmd, 10).trim();
                try {
                    monitor.put("memoryUsage", Double.parseDouble(memResult));
                } catch (NumberFormatException e) {
                    monitor.put("memoryUsage", 0.0);
                }

                // Memory details
                String memDetailCmd = "free -m | grep Mem | awk '{print $2, $3}' 2>/dev/null || echo '0 0'";
                String memDetail = sshManager.executeCommand(ssh, memDetailCmd, 10).trim();
                String[] memParts = memDetail.split("\\s+");
                if (memParts.length >= 2) {
                    monitor.put("memoryTotalMb", Integer.parseInt(memParts[0]));
                    monitor.put("memoryUsedMb", Integer.parseInt(memParts[1]));
                }

                // Disk usage
                String diskCmd = String.format("df -h %s | tail -1 | awk '{print $5, $3, $2}' 2>/dev/null || echo '0%% 0 0'", inst.getDeployPath());
                String diskResult = sshManager.executeCommand(ssh, diskCmd, 10).trim();
                String[] diskParts = diskResult.split("\\s+");
                if (diskParts.length >= 1) {
                    String diskPct = diskParts[0].replace("%", "");
                    try {
                        monitor.put("diskUsage", Double.parseDouble(diskPct));
                    } catch (NumberFormatException e) {
                        monitor.put("diskUsage", 0.0);
                    }
                }
                if (diskParts.length >= 2) {
                    monitor.put("diskUsedGb", diskParts[1]);
                }
                if (diskParts.length >= 3) {
                    monitor.put("diskTotalGb", diskParts[2]);
                }

                // Process status
                String pid = inst.getPid() != null ? inst.getPid().toString() : "";
                String procCmd = String.format("ps -p %s -o pid,%%cpu,%%mem 2>/dev/null || echo 'not_running'", pid);
                String procResult = sshManager.executeCommand(ssh, procCmd, 5).trim();
                monitor.put("processStatus", inst.getProcessStatus());
                monitor.put("processOutput", procResult);

                return JSONUtil.toJsonStr(monitor);
            } finally {
                ssh.close();
            }
        } catch (Exception e) {
            throw new BusinessException("获取监控数据失败: " + e.getMessage());
        }
    }

    @Override
    public List<String> getVersions(Long instanceId) {
        ServiceInstance inst = getByIdWithChecks(instanceId);
        Server server = serverMapper.selectById(inst.getServerId());
        if (server == null) throw new BusinessException("服务器不存在");
        String basePath = inst.getDeployPath();
        try {
            SSHClient ssh = sshManager.connect(server);
            try {
                String listCmd = String.format("ls -lt %s/versions 2>/dev/null | grep '^d' | awk '{print $NF}' | head -20", basePath);
                String output = sshManager.executeCommand(ssh, listCmd, 10);
                List<String> versions = new ArrayList<>();
                for (String line : output.split("\n")) {
                    String v = line.trim();
                    if (!v.isEmpty()) {
                        versions.add(v);
                    }
                }
                return versions;
            } finally {
                ssh.close();
            }
        } catch (Exception e) {
            throw new BusinessException("获取版本列表失败: " + e.getMessage());
        }
    }

    private ServiceInstance getByIdWithChecks(Long id) {
        ServiceInstance inst = getById(id);
        if (inst == null) throw new BusinessException("服务实例不存在");
        return inst;
    }
}
