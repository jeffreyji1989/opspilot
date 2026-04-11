package com.opspilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.opspilot.common.BusinessException;
import com.opspilot.common.SshManager;
import com.opspilot.dto.DeployRequest;
import com.opspilot.entity.DeployRecord;
import com.opspilot.entity.DeploySchedule;
import com.opspilot.entity.DeployStep;
import com.opspilot.entity.OperationLog;
import com.opspilot.entity.Server;
import com.opspilot.entity.ServiceInstance;
import com.opspilot.entity.User;
import com.opspilot.entity.Project;
import com.opspilot.entity.GitCredential;
import com.opspilot.mapper.*;
import com.opspilot.service.DeployService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeployServiceImpl extends ServiceImpl<DeployRecordMapper, DeployRecord> implements DeployService {

    private final ModuleMapper moduleMapper;
    private final ServerMapper serverMapper;
    private final ServiceInstanceMapper instanceMapper;
    private final DeployStepMapper deployStepMapper;
    private final GitCredentialMapper gitCredentialMapper;
    private final SshManager sshManager;

    // Track active deploys (instanceId -> deployRecordId)
    private static final Map<Long, Long> activeDeploys = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public Long startDeploy(DeployRequest request, Long operatorId, String username) {
        Long instanceId = request.getInstanceId();
        if (activeDeploys.containsKey(instanceId)) {
            throw new BusinessException(50001, "该服务正在部署中，请稍后再试");
        }

        ServiceInstance inst = instanceMapper.selectById(instanceId);
        if (inst == null) throw new BusinessException("服务实例不存在");
        com.opspilot.entity.Module module = moduleMapper.selectById(inst.getModuleId());
        if (module == null) throw new BusinessException("模块不存在");
        Server server = serverMapper.selectById(inst.getServerId());
        if (server == null) throw new BusinessException("服务器不存在");

        // Generate deploy number
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long todayCount = count(new LambdaQueryWrapper<DeployRecord>()
                .likeRight(DeployRecord::getDeployNo, "DEP-" + dateStr));
        String deployNo = String.format("DEP-%s-%03d", dateStr, todayCount + 1);

        // Generate version
        String version = String.format("%s_%03d_%s", dateStr, todayCount + 1, request.getGitBranch().replaceAll("[^a-zA-Z0-9._-]", "_"));

        DeployRecord record = new DeployRecord();
        record.setDeployNo(deployNo);
        record.setModuleId(inst.getModuleId());
        record.setInstanceId(instanceId);
        record.setVersion(version);
        record.setGitBranch(request.getGitBranch());
        record.setGitCommit(request.getGitCommit() != null ? request.getGitCommit() : "");
        record.setDeployType(request.getDeployType());
        record.setScheduleId(request.getScheduleId());
        record.setOperatorId(operatorId);
        record.setStatus(0);
        record.setStartTime(LocalDateTime.now());
        save(record);

        activeDeploys.put(instanceId, record.getId());

        // Execute deployment asynchronously
        new Thread(() -> executeDeploy(record, module, server, inst, username)).start();

        return record.getId();
    }

    private void executeDeploy(DeployRecord record, com.opspilot.entity.Module module, Server server, ServiceInstance inst, String username) {
        Long recordId = record.getId();
        try {
            String basePath = inst.getDeployPath();

            // Step 1: Pull code
            updateStep(recordId, 1, "pull_code", "拉取代码");
            SSHClient ssh = sshManager.connect(server);
            String gitPath = basePath + "/build";
            String cloneCmd = String.format(
                    "mkdir -p %s && cd %s && ( [ -d .git ] && git fetch origin || git clone %s . ) && git checkout %s 2>&1",
                    gitPath, gitPath, module.getRepoUrl(), record.getGitBranch());
            String pullOutput = sshManager.executeCommand(ssh, cloneCmd, 120);
            completeStep(recordId, pullOutput);

            // Step 2: Build
            updateStep(recordId, 2, "build", "编译构建");
            String subPath = StringUtils.hasText(module.getRepoPath()) ? module.getRepoPath() + "/" : "";
            String buildPath = gitPath + "/" + subPath;
            String buildCmd = StringUtils.hasText(module.getBuildCommand()) ? module.getBuildCommand() :
                    "mvn clean package -pl " + module.getModuleName() + " -am -DskipTests";
            String buildOutput = sshManager.executeCommand(ssh, "cd " + buildPath + " && " + buildCmd + " 2>&1", 600);
            completeStep(recordId, buildOutput);

            // Step 3: Deploy (copy to version dir)
            updateStep(recordId, 3, "deploy", "部署");
            String versionDir = basePath + "/versions/" + record.getVersion();
            String artifactPath = module.getArtifactPath();
            String deployCmd = String.format(
                    "mkdir -p %s && cp %s/%s %s/app.jar 2>/dev/null; " +
                    "cp -r %s/scripts/* %s/scripts/ 2>/dev/null; " +
                    // Generate start.sh
                    "cat > %s/scripts/start.sh << 'SCRIPT'\n" +
                    "#!/bin/bash\n" +
                    "cd %s\n" +
                    "nohup java %s -jar app.jar --server.port=%d > ../logs/startup.log 2>&1 &\n" +
                    "echo $! > ../pid\n" +
                    "sleep %d\n" +
                    "echo 'Service started'\n" +
                    "SCRIPT\n" +
                    "chmod +x %s/scripts/start.sh; " +
                    // Generate stop.sh
                    "cat > %s/scripts/stop.sh << 'SCRIPT'\n" +
                    "#!/bin/bash\n" +
                    "if [ -f ../pid ]; then\n" +
                    "  PID=$(cat ../pid)\n" +
                    "  kill $PID 2>/dev/null\n" +
                    "  for i in $(seq 1 30); do kill -0 $PID 2>/dev/null || break; sleep 1; done\n" +
                    "  kill -0 $PID 2>/dev/null && kill -9 $PID\n" +
                    "  rm -f ../pid\n" +
                    "fi\n" +
                    "SCRIPT\n" +
                    "chmod +x %s/scripts/stop.sh; " +
                    // Switch symlink
                    "ln -sfn %s %s/current",
                    versionDir,
                    buildPath, artifactPath, versionDir,
                    gitPath + "/" + subPath, basePath,
                    basePath, versionDir, inst.getJvmOptions() != null ? inst.getJvmOptions() : "",
                    inst.getListenPort(), 30,
                    basePath,
                    basePath, basePath,
                    versionDir, basePath);
            String deployOutput = sshManager.executeCommand(ssh, deployCmd, 30);
            completeStep(recordId, deployOutput);

            // Step 4: Health check
            updateStep(recordId, 4, "health_check", "健康检查");
            String healthPort = inst.getHealthCheckPort() != null ? inst.getHealthCheckPort().toString() : inst.getListenPort().toString();
            String healthPath = StringUtils.hasText(inst.getHealthCheckPath()) ? inst.getHealthCheckPath() : "/";
            String checkCmd = String.format("sleep 5 && curl -sf http://localhost:%s%s -o /dev/null -w '%%{http_code}' 2>/dev/null || echo '000'", healthPort, healthPath);
            String healthOutput = sshManager.executeCommand(ssh, checkCmd, 60);
            boolean healthy = healthOutput.trim().startsWith("200");

            if (healthy) {
                completeStep(recordId, "Health check passed: " + healthOutput);
                inst.setProcessStatus(1);
                inst.setCurrentVersion(record.getVersion());
                // Try to get PID
                String pidStr = sshManager.executeCommand(ssh, "cat " + basePath + "/pid 2>/dev/null", 5);
                if (StringUtils.hasText(pidStr)) {
                    inst.setPid(Integer.parseInt(pidStr.trim()));
                }
                instanceMapper.updateById(inst);
                record.setStatus(5); // success
            } else {
                failStep(recordId, "Health check failed: HTTP " + healthOutput);
                record.setStatus(6); // failed
                record.setErrorMessage("健康检查未通过: " + healthOutput);
            }

            record.setEndTime(LocalDateTime.now());
            record.setDurationSeconds((int) (System.currentTimeMillis() - record.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) / 1000);
            updateById(record);

        } catch (Exception e) {
            log.error("Deployment failed: {}", e.getMessage(), e);
            record.setStatus(6);
            record.setErrorMessage(e.getMessage());
            record.setEndTime(LocalDateTime.now());
            updateById(record);
            try {
                DeployStep step = deployStepMapper.selectOne(new LambdaQueryWrapper<DeployStep>()
                        .eq(DeployStep::getDeployRecordId, recordId)
                        .eq(DeployStep::getStatus, 1).last("LIMIT 1"));
                if (step != null) {
                    step.setStatus(3);
                    step.setErrorMessage(e.getMessage());
                    step.setEndTime(LocalDateTime.now());
                    deployStepMapper.updateById(step);
                }
            } catch (Exception ignored) {}
        } finally {
            activeDeploys.remove(inst.getId());
        }
    }

    private void updateStep(Long recordId, int order, String name, String displayName) {
        DeployStep step = new DeployStep();
        step.setDeployRecordId(recordId);
        step.setStepName(name);
        step.setStepOrder(order);
        step.setStatus(1);
        step.setStartTime(LocalDateTime.now());
        deployStepMapper.insert(step);
    }

    private void completeStep(Long recordId, String logOutput) {
        DeployStep step = deployStepMapper.selectOne(new LambdaQueryWrapper<DeployStep>()
                .eq(DeployStep::getDeployRecordId, recordId)
                .eq(DeployStep::getStatus, 1).last("LIMIT 1"));
        if (step != null) {
            step.setStatus(2);
            step.setEndTime(LocalDateTime.now());
            step.setLogOutput(truncate(logOutput, 4000));
            deployStepMapper.updateById(step);
        }
    }

    private void failStep(Long recordId, String errorMsg) {
        DeployStep step = deployStepMapper.selectOne(new LambdaQueryWrapper<DeployStep>()
                .eq(DeployStep::getDeployRecordId, recordId)
                .eq(DeployStep::getStatus, 1).last("LIMIT 1"));
        if (step != null) {
            step.setStatus(3);
            step.setErrorMessage(errorMsg);
            step.setEndTime(LocalDateTime.now());
            deployStepMapper.updateById(step);
        }
    }

    @Override
    public IPage<DeployRecord> pageDeployRecords(int pageNum, int pageSize, Long instanceId) {
        LambdaQueryWrapper<DeployRecord> wrapper = new LambdaQueryWrapper<>();
        if (instanceId != null) wrapper.eq(DeployRecord::getInstanceId, instanceId);
        wrapper.orderByDesc(DeployRecord::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public void cancelDeploy(Long deployRecordId) {
        DeployRecord record = getById(deployRecordId);
        if (record == null) throw new BusinessException("发版记录不存在");
        if (record.getStatus() != 0 && record.getStatus() != 1 && record.getStatus() != 2) {
            throw new BusinessException("当前状态不可取消");
        }
        record.setStatus(6);
        record.setErrorMessage("用户手动取消");
        record.setEndTime(LocalDateTime.now());
        updateById(record);
    }

    @Override
    public String getDeployLog(Long deployRecordId) {
        java.util.List<DeployStep> steps = deployStepMapper.selectList(
                new LambdaQueryWrapper<DeployStep>()
                        .eq(DeployStep::getDeployRecordId, deployRecordId)
                        .orderByAsc(DeployStep::getStepOrder));
        StringBuilder sb = new StringBuilder();
        for (DeployStep step : steps) {
            sb.append(String.format("=== Step %d: %s [%s] ===%n", step.getStepOrder(), step.getStepName(), step.getStatus()));
            if (StringUtils.hasText(step.getLogOutput())) {
                sb.append(step.getLogOutput()).append("\n");
            }
            if (StringUtils.hasText(step.getErrorMessage())) {
                sb.append("ERROR: ").append(step.getErrorMessage()).append("\n");
            }
        }
        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
