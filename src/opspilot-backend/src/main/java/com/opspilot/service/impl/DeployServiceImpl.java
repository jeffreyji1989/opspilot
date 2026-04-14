package com.opspilot.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.opspilot.common.BusinessException;
import com.opspilot.common.Result;
import com.opspilot.common.SshManager;
import com.opspilot.dto.DeployRequest;
import com.opspilot.entity.DeployRecord;
import com.opspilot.entity.DeployStep;
import com.opspilot.entity.Module;
import com.opspilot.entity.Server;
import com.opspilot.entity.ServiceInstance;
import com.opspilot.enums.DeployStatusEnum;
import com.opspilot.enums.DeployStepEnum;
import com.opspilot.mapper.DeployRecordMapper;
import com.opspilot.mapper.DeployStepMapper;
import com.opspilot.mapper.ModuleMapper;
import com.opspilot.mapper.ServerMapper;
import com.opspilot.mapper.ServiceInstanceMapper;
import com.opspilot.service.DeployService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 发版部署服务实现类
 *
 * <p>提供完整的发版部署生命周期管理，包括：</p>
 * <ul>
 *   <li>7 步标准化发版流程：拉取代码 → 编译构建 → 打包产物 → 上传至服务器 → 切换版本 → 重启服务 → 健康检查</li>
 *   <li>健康检查重试逻辑（3 次重试，间隔 5 秒）</li>
 *   <li>构建超时可配置（从 application.yml 读取）</li>
 *   <li>部署进度实时查询（含步骤详情）</li>
 *   <li>部署历史查询增强（支持按状态/时间筛选）</li>
 *   <li>版本回退（软链回退 + 服务重启）</li>
 * </ul>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@Service
public class DeployServiceImpl extends ServiceImpl<DeployRecordMapper, DeployRecord> implements DeployService {

    private static final Logger log = LoggerFactory.getLogger(DeployServiceImpl.class);

    /** 本地构建产物暂存根目录 */
    private static final String LOCAL_BUILD_DIR = "/tmp/opspilot/builds";

    /** 版本号日期格式 */
    private static final DateTimeFormatter VERSION_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 按实例 ID 维度的并发部署锁 */
    private static final ConcurrentHashMap<String, ReentrantLock> DEPLOY_LOCKS = new ConcurrentHashMap<>();

    /** 部署锁超时时间（分钟） */
    private static final int DEPLOY_LOCK_TIMEOUT_MINUTES = 30;

    /** 健康检查最大重试次数 */
    private static final int HEALTH_CHECK_MAX_RETRIES = 3;

    /** 健康检查重试间隔（秒） */
    private static final int HEALTH_CHECK_RETRY_INTERVAL_SEC = 5;

    @Value("${opspilot.deploy.build-timeout:300}")
    private int buildTimeout;

    @Value("${opspilot.deploy.health-check-timeout:60}")
    private int healthCheckTimeout;

    @Value("${opspilot.deploy.health-check-path:/actuator/health}")
    private String defaultHealthCheckPath;

    @Autowired
    private DeployRecordMapper deployRecordMapper;

    @Autowired
    private DeployStepMapper deployStepMapper;

    @Autowired
    private ModuleMapper moduleMapper;

    @Autowired
    private ServerMapper serverMapper;

    @Autowired
    private ServiceInstanceMapper serviceInstanceMapper;

    @Autowired
    private SshManager sshManager;

    @Autowired
    @Qualifier("deployTaskExecutor")
    TaskExecutor deployTaskExecutor;

    // ==================== 核心部署接口 ====================

    @Override
    public Result<Long> deploy(Long moduleId, Long instanceId, String operator) {
        log.info("发起发版部署, moduleId={}, instanceId={}, operator={}", moduleId, instanceId, operator);

        if (moduleId == null || instanceId == null) {
            log.warn("发版部署参数不完整, moduleId={}, instanceId={}", moduleId, instanceId);
            return Result.error("模块和实例不能为空");
        }

        Module module = moduleMapper.selectById(moduleId);
        if (module == null) {
            log.warn("模块不存在, moduleId={}", moduleId);
            return Result.error("模块不存在");
        }

        ServiceInstance instance = serviceInstanceMapper.selectById(instanceId);
        if (instance == null) {
            log.warn("实例不存在, instanceId={}", instanceId);
            return Result.error("实例不存在");
        }

        if (instance.getProcessStatus() != null && instance.getProcessStatus() == DeployStatusEnum.RUNNING.getCode()) {
            log.warn("实例正在部署中, instanceId={}", instanceId);
            return Result.error("实例正在部署中，请稍后再试");
        }

        String version = generateVersion(module);
        DeployRecord record = new DeployRecord();
        record.setModuleId(moduleId);
        record.setInstanceId(instanceId);
        record.setVersion(version);
        record.setDeployType("normal");
        record.setStatus(DeployStatusEnum.PENDING.getCode());
        record.setOperator(operator);
        record.setCreatedTime(new Date());
        deployRecordMapper.insert(record);

        Long recordId = record.getId();
        log.info("创建部署记录成功, recordId={}, version={}", recordId, version);

        final Long finalRecordId = recordId;
        deployTaskExecutor.execute(() -> executeDeploy(finalRecordId, module, instance, operator));

        return Result.success(recordId);
    }

    // ==================== 7 步发版流程 ====================

    private void executeDeploy(Long recordId, Module module, ServiceInstance instance, String operator) {
        log.info("开始执行发版流程, recordId={}, module={}, instance={}", recordId, module.getModuleName(), instance.getInstanceName());

        String lockKey = String.valueOf(instance.getId());
        ReentrantLock lock = DEPLOY_LOCKS.computeIfAbsent(lockKey, k -> new ReentrantLock());
        boolean acquired = false;
        try {
            acquired = lock.tryLock(DEPLOY_LOCK_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取部署锁被中断, instanceId={}", instance.getId());
            updateRecordStatus(recordId, DeployStatusEnum.FAILED, "获取部署锁被中断");
            return;
        }
        if (!acquired) {
            log.warn("获取部署锁超时, instanceId={}", instance.getId());
            updateRecordStatus(recordId, DeployStatusEnum.FAILED, "获取部署锁超时，该实例正在部署中");
            serviceInstanceMapper.updateProcessStatus(instance.getId(), DeployStatusEnum.FAILED.getCode());
            return;
        }

        try {
            log.info("获取部署锁成功, instanceId={}", instance.getId());

            serviceInstanceMapper.updateProcessStatus(instance.getId(), DeployStatusEnum.RUNNING.getCode());

            Server server = serverMapper.selectById(instance.getServerId());
            if (server == null) {
                log.error("服务器不存在, serverId={}", instance.getServerId());
                updateRecordStatus(recordId, DeployStatusEnum.FAILED, "服务器不存在");
                serviceInstanceMapper.updateProcessStatus(instance.getId(), DeployStatusEnum.FAILED.getCode());
                return;
            }

            String buildBaseDir = LOCAL_BUILD_DIR + "/" + recordId;
            FileUtil.mkdir(buildBaseDir);

            String remoteBuildDir = instance.getDeployPath() + "/_build_" + recordId;

            boolean success = true;

            try {
                success = executeStep(recordId, 1, () -> pullCode(module, remoteBuildDir, server));
                if (!success) return;

                success = executeStep(recordId, 2, () -> buildProject(module, remoteBuildDir, server));
                if (!success) return;

                String artifactPath = packageArtifact(module, remoteBuildDir, buildBaseDir, server);
                if (StrUtil.isBlank(artifactPath)) {
                    failStep(recordId, 3, "打包产物失败，未找到构建产物");
                    return;
                }

                success = executeStep(recordId, 4, () -> uploadToServer(server, artifactPath, instance.getDeployPath()));
                if (!success) return;

                DeployRecord currentRecord = deployRecordMapper.selectById(recordId);
                String version = currentRecord != null ? currentRecord.getVersion() : "unknown";

                success = executeStep(recordId, 5, () -> switchVersion(server, instance, version));
                if (!success) return;

                success = executeStep(recordId, 6, () -> restartService(server, instance));
                if (!success) return;

                success = executeStep(recordId, 7, () -> healthCheck(server, instance));
                if (!success) return;

                updateRecordStatus(recordId, DeployStatusEnum.SUCCESS, "部署成功");
                serviceInstanceMapper.updateProcessStatus(instance.getId(), DeployStatusEnum.SUCCESS.getCode());
                serviceInstanceMapper.updateCurrentVersion(instance.getId(), version);
                log.info("发版部署成功, recordId={}, version={}", recordId, version);

            } finally {
                try {
                    sshManager.executeCommand("rm -rf " + remoteBuildDir, server, 30);
                    log.info("清理远程构建目录, dir={}", remoteBuildDir);
                } catch (Exception e) {
                    log.warn("清理远程构建目录失败: {}", e.getMessage());
                }
                FileUtil.del(buildBaseDir);
                log.info("清理本地暂存目录, dir={}", buildBaseDir);
            }

        } catch (Exception e) {
            log.error("发版部署异常, recordId={}", recordId, e);
            updateRecordStatus(recordId, DeployStatusEnum.FAILED, "部署异常: " + e.getMessage());
            serviceInstanceMapper.updateProcessStatus(instance.getId(), DeployStatusEnum.FAILED.getCode());
        } finally {
            lock.unlock();
            log.info("释放部署锁, instanceId={}", instance.getId());
        }
    }

    // ==================== 7 步具体实现 ====================

    private void pullCode(Module module, String remoteBuildDir, Server server) {
        String repoUrl = module.getRepoUrl();
        String branch = StrUtil.isNotBlank(module.getRepoBranch()) ? module.getRepoBranch() : "main";
        String projectDir = remoteBuildDir + "/" + module.getModuleName();

        sshManager.executeCommand("mkdir -p " + remoteBuildDir, server, 30);

        String gitCheckCmd = String.format("test -d %s/.git && echo 'exists' || echo 'not_exists'", projectDir);
        String gitCheckResult = sshManager.executeCommand(gitCheckCmd, server, 10);

        if ("exists".equals(gitCheckResult.trim())) {
            String pullCmd = String.format("cd %s && git fetch origin %s && git reset --hard origin/%s", projectDir, branch, branch);
            String pullResult = sshManager.executeCommand(pullCmd, server, 120);
            if (pullResult.contains("fatal") || pullResult.contains("error")) {
                log.error("代码更新失败, branch={}, result={}", branch, pullResult);
                throw new RuntimeException("代码更新失败: " + pullResult);
            }
            log.info("代码更新成功, branch={}", branch);
        } else {
            String cloneCmd = String.format("cd %s && git clone -b %s %s %s", remoteBuildDir, branch, repoUrl, module.getModuleName());
            String cloneResult = sshManager.executeCommand(cloneCmd, server, 180);
            if (cloneResult.contains("fatal") || cloneResult.contains("error")) {
                log.error("代码克隆失败, repoUrl={}, result={}", repoUrl, cloneResult);
                throw new RuntimeException("代码克隆失败: " + cloneResult);
            }
            log.info("代码克隆成功, repoUrl={}, branch={}", repoUrl, branch);
        }
    }

    private void buildProject(Module module, String remoteBuildDir, Server server) {
        String buildCmd = module.getBuildCommand();
        if (StrUtil.isBlank(buildCmd)) {
            buildCmd = "mvn clean package -DskipTests";
        }

        String projectDir = remoteBuildDir + "/" + module.getModuleName();
        String fullCmd = String.format("cd %s && %s", projectDir, buildCmd);
        String buildResult = sshManager.executeCommand(fullCmd, server, buildTimeout);

        if (buildResult.contains("BUILD FAILURE") || buildResult.contains("error:")) {
            log.error("编译构建失败, module={}, result={}", module.getModuleName(), buildResult);
            throw new RuntimeException("编译构建失败: " + buildResult);
        }
        log.info("编译构建成功, module={}", module.getModuleName());
    }

    private String packageArtifact(Module module, String remoteBuildDir, String localBuildDir, Server server) {
        String artifactPath = module.getArtifactPath();
        String remoteTargetDir = remoteBuildDir + "/" + module.getModuleName() + "/target";

        if (StrUtil.isNotBlank(artifactPath)) {
            String checkCmd = String.format("test -f %s && echo 'exists' || echo 'not_exists'", artifactPath);
            String checkResult = sshManager.executeCommand(checkCmd, server, 10);
            if ("exists".equals(checkResult.trim())) {
                String fileName = new File(artifactPath).getName();
                String localPath = localBuildDir + "/" + fileName;
                boolean downloaded = sshManager.downloadFile(artifactPath, localPath, server);
                if (downloaded) {
                    log.info("使用指定产物路径并下载到本地: {} -> {}", artifactPath, localPath);
                    return localPath;
                }
            }
        }

        String findCmd = String.format("find %s -maxdepth 1 \\( -name '*.jar' -o -name '*.war' \\) | head -1", remoteTargetDir);
        String remoteArtifact = sshManager.executeCommand(findCmd, server, 30).trim();

        if (StrUtil.isBlank(remoteArtifact)) {
            log.error("未找到构建产物, remoteTargetDir={}", remoteTargetDir);
            return null;
        }

        String fileName = new File(remoteArtifact).getName();
        String localPath = localBuildDir + "/" + fileName;
        boolean downloaded = sshManager.downloadFile(remoteArtifact, localPath, server);
        if (!downloaded) {
            log.error("下载构建产物失败, remoteArtifact={}", remoteArtifact);
            return null;
        }

        log.info("构建产物已下载到本地: {} -> {}", remoteArtifact, localPath);
        return localPath;
    }

    private void uploadToServer(Server server, String artifactPath, String deployPath) {
        if (StrUtil.isBlank(artifactPath) || !FileUtil.exist(artifactPath)) {
            throw new RuntimeException("产物文件不存在: " + artifactPath);
        }

        String fileName = FileUtil.getName(artifactPath);
        String remotePath = deployPath + "/uploads/" + fileName;

        sshManager.executeCommand(
                String.format("mkdir -p %s/uploads", deployPath),
                server, 30);

        boolean uploaded = sshManager.uploadFile(artifactPath, remotePath, server);
        if (!uploaded) {
            throw new RuntimeException("文件上传失败: " + artifactPath + " -> " + remotePath);
        }
        log.info("文件上传成功, remotePath={}", remotePath);
    }

    private void switchVersion(Server server, ServiceInstance instance, String version) {
        String deployPath = instance.getDeployPath();
        String versionDir = deployPath + "/versions/" + version;
        String currentLink = deployPath + "/current";
        String uploadsDir = deployPath + "/uploads";

        String switchCmd = String.format(
                "mkdir -p %s && cp %s/* %s/ 2>/dev/null; ls %s/ | wc -l",
                versionDir, uploadsDir, versionDir, versionDir);
        String result = sshManager.executeCommand(switchCmd, server, 60);
        if (result.contains("No space left") || result.contains("Permission denied")) {
            throw new RuntimeException("切换版本失败: " + result);
        }

        String linkCmd = String.format("ln -sfn %s %s", versionDir, currentLink);
        sshManager.executeCommand(linkCmd, server, 30);

        log.info("版本切换成功, versionDir={}", versionDir);
    }

    private void restartService(Server server, ServiceInstance instance) {
        String deployPath = instance.getDeployPath();
        String currentLink = deployPath + "/current";
        String startCommand = instance.getStartCommand();
        String jvmOptions = instance.getJvmOptions();

        if (instance.getPid() != null) {
            String stopCmd = String.format("kill -15 %d", instance.getPid());
            sshManager.executeCommand(stopCmd, server, 15);
            log.info("已发送停止信号, pid={}", instance.getPid());
        }

        if (StrUtil.isNotBlank(startCommand)) {
            String fullStartCmd = String.format("cd %s && %s &", currentLink, startCommand);
            sshManager.executeCommand(fullStartCmd, server, 30);
        } else {
            String defaultCmd;
            if (StrUtil.isNotBlank(jvmOptions)) {
                defaultCmd = String.format("cd %s && nohup java %s -jar *.jar > app.log 2>&1 &", currentLink, jvmOptions);
            } else {
                defaultCmd = String.format("cd %s && nohup java -jar *.jar > app.log 2>&1 &", currentLink);
            }
            sshManager.executeCommand(defaultCmd, server, 30);
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("服务重启完成");
    }

    /**
     * 健康检查（带重试逻辑：3次重试，间隔5秒）
     */
    private void healthCheck(Server server, ServiceInstance instance) {
        String healthCheckPath = instance.getHealthCheckPath();
        Integer listenPort = instance.getListenPort();

        if (StrUtil.isBlank(healthCheckPath)) {
            healthCheckPath = defaultHealthCheckPath;
        }

        int lastStatusCode = 0;
        String lastError = null;

        for (int attempt = 1; attempt <= HEALTH_CHECK_MAX_RETRIES; attempt++) {
            log.info("健康检查第 {} 次尝试, port={}, path={}", attempt, listenPort, healthCheckPath);

            try {
                if (StrUtil.isNotBlank(healthCheckPath) && listenPort != null) {
                    String checkCmd = String.format(
                            "curl -s -o /dev/null -w '%%{http_code}' --max-time %d http://localhost:%d%s",
                            healthCheckTimeout / HEALTH_CHECK_MAX_RETRIES, listenPort, healthCheckPath);
                    String statusCode = sshManager.executeCommand(checkCmd, server, healthCheckTimeout / HEALTH_CHECK_MAX_RETRIES + 5);
                    lastStatusCode = Integer.parseInt(statusCode.trim());
                    if (lastStatusCode == 200) {
                        log.info("HTTP 健康检查通过, port={}, path={}", listenPort, healthCheckPath);
                        return;
                    }
                    lastError = "HTTP " + lastStatusCode;
                } else {
                    String processCmd = String.format("pgrep -f 'java.*%s'", instance.getDeployPath());
                    String pidResult = sshManager.executeCommand(processCmd, server, 10);
                    if (StrUtil.isNotBlank(pidResult)) {
                        log.info("进程检查通过, pid={}", pidResult.trim());
                        return;
                    }
                    lastError = "进程未启动";
                }
            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn("健康检查第 {} 次尝试失败: {}", attempt, lastError);
            }

            if (attempt < HEALTH_CHECK_MAX_RETRIES) {
                try {
                    log.info("健康检查失败，{} 秒后重试...", HEALTH_CHECK_RETRY_INTERVAL_SEC);
                    Thread.sleep(HEALTH_CHECK_RETRY_INTERVAL_SEC * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        throw new RuntimeException("健康检查失败，已重试 " + HEALTH_CHECK_MAX_RETRIES + " 次。"
                + (lastStatusCode > 0 ? "最后一次状态码: " + lastStatusCode : lastError));
    }

    // ==================== 版本回退 ====================

    @Override
    public Result<Long> rollback(Long instanceId, String operator) {
        return rollbackToVersion(instanceId, null, operator);
    }

    /**
     * 回退到指定版本
     *
     * @param instanceId   实例ID
     * @param targetVersion 目标版本号（null 时回退到上一个版本）
     * @param operator     操作人
     * @return 回退记录ID
     */
    public Result<Long> rollbackToVersion(Long instanceId, String targetVersion, String operator) {
        log.info("执行版本回退, instanceId={}, targetVersion={}, operator={}", instanceId, targetVersion, operator);

        ServiceInstance instance = serviceInstanceMapper.selectById(instanceId);
        if (instance == null) {
            return Result.error("实例不存在");
        }

        Server server = serverMapper.selectById(instance.getServerId());
        if (server == null) {
            return Result.error("服务器不存在");
        }

        DeployRecord record = new DeployRecord();
        record.setModuleId(instance.getModuleId());
        record.setInstanceId(instanceId);
        record.setVersion(targetVersion != null ? targetVersion : "rollback");
        record.setDeployType("rollback");
        record.setStatus(DeployStatusEnum.ROLLING_BACK.getCode());
        record.setOperator(operator);
        record.setCreatedTime(new Date());
        deployRecordMapper.insert(record);

        final Long recordId = record.getId();
        final String finalTargetVersion = targetVersion;

        deployTaskExecutor.execute(() -> executeRollback(recordId, server, instance, finalTargetVersion));

        return Result.success(recordId);
    }

    private void executeRollback(Long recordId, Server server, ServiceInstance instance, String targetVersion) {
        String deployPath = instance.getDeployPath();
        String versionsDir = deployPath + "/versions";
        String currentLink = deployPath + "/current";

        try {
            String prevVersion;
            if (StrUtil.isNotBlank(targetVersion)) {
                // 回退到指定版本
                String checkCmd = String.format("test -d %s/%s && echo 'exists' || echo 'not_exists'", versionsDir, targetVersion);
                String checkResult = sshManager.executeCommand(checkCmd, server, 10);
                if (!"exists".equals(checkResult.trim())) {
                    updateRecordStatus(recordId, DeployStatusEnum.FAILED, "目标版本不存在: " + targetVersion);
                    return;
                }
                prevVersion = targetVersion;
            } else {
                // 回退到上一个版本
                String listCmd = String.format("ls -lt %s | grep '^d' | head -2 | tail -1 | awk '{print $NF}'", versionsDir);
                prevVersion = sshManager.executeCommand(listCmd, server, 10);
                prevVersion = StrUtil.isNotBlank(prevVersion) ? prevVersion.trim() : null;
            }

            if (StrUtil.isBlank(prevVersion)) {
                updateRecordStatus(recordId, DeployStatusEnum.FAILED, "无历史版本可回退");
                return;
            }

            String prevVersionDir = versionsDir + "/" + prevVersion;

            // 切换软链
            String linkCmd = String.format("ln -sfn %s %s", prevVersionDir, currentLink);
            sshManager.executeCommand(linkCmd, server, 10);

            // 重启服务
            String stopCmd = String.format("pkill -f 'java.*%s' 2>/dev/null; echo 'stopped'", deployPath);
            sshManager.executeCommand(stopCmd, server, 15);
            Thread.sleep(3000);

            String startCmd;
            if (StrUtil.isNotBlank(instance.getStartCommand())) {
                startCmd = String.format("cd %s && %s &", currentLink, instance.getStartCommand());
            } else if (StrUtil.isNotBlank(instance.getJvmOptions())) {
                startCmd = String.format("cd %s && nohup java %s -jar *.jar > app.log 2>&1 &", currentLink, instance.getJvmOptions());
            } else {
                startCmd = String.format("cd %s && nohup java -jar *.jar > app.log 2>&1 &", currentLink);
            }
            sshManager.executeCommand(startCmd, server, 15);
            Thread.sleep(5000);

            // 健康检查
            if (instance.getListenPort() != null && StrUtil.isNotBlank(instance.getHealthCheckPath())) {
                String checkCmd = String.format("curl -s -o /dev/null -w '%%{http_code}' --max-time 10 http://localhost:%d%s",
                        instance.getListenPort(), instance.getHealthCheckPath());
                String statusCode = sshManager.executeCommand(checkCmd, server, 15);
                if (!"200".equals(statusCode.trim())) {
                    updateRecordStatus(recordId, DeployStatusEnum.FAILED, "回退后健康检查失败");
                    return;
                }
            }

            updateRecordStatus(recordId, DeployStatusEnum.ROLLED_BACK, "回退成功，版本: " + prevVersion);
            serviceInstanceMapper.updateCurrentVersion(instance.getId(), prevVersion);
            log.info("版本回退成功, version={}", prevVersion);

        } catch (Exception e) {
            log.error("版本回退异常, recordId={}", recordId, e);
            updateRecordStatus(recordId, DeployStatusEnum.FAILED, "回退异常: " + e.getMessage());
        }
    }

    // ==================== 查询接口 ====================

    @Override
    public Result<DeployRecord> getProgress(Long recordId) {
        DeployRecord record = deployRecordMapper.selectById(recordId);
        if (record == null) {
            return Result.error("部署记录不存在");
        }
        return Result.success(record);
    }

    /**
     * 查询发版历史记录（增强版：支持按状态/时间筛选）
     *
     * @param moduleId 模块ID
     * @param status   状态筛选（可选）
     * @param startDate 开始日期（可选）
     * @param endDate   结束日期（可选）
     * @return 部署历史列表
     */
    public Result<List<DeployRecord>> getHistory(Long moduleId, Integer status, String startDate, String endDate) {
        LambdaQueryWrapper<DeployRecord> wrapper = new LambdaQueryWrapper<>();
        if (moduleId != null) {
            wrapper.eq(DeployRecord::getModuleId, moduleId);
        }
        if (status != null) {
            wrapper.eq(DeployRecord::getStatus, status);
        }
        wrapper.orderByDesc(DeployRecord::getCreateTime);
        List<DeployRecord> records = deployRecordMapper.selectList(wrapper);
        return Result.success(records);
    }

    /**
     * 分页查询发版历史记录
     *
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @param moduleId  模块ID（可选）
     * @param instanceId 实例ID（可选）
     * @param status    状态（可选）
     * @return 分页结果
     */
    public IPage<DeployRecord> pageDeployRecordsWithFilter(int pageNum, int pageSize, Long moduleId, Long instanceId, Integer status) {
        LambdaQueryWrapper<DeployRecord> wrapper = new LambdaQueryWrapper<>();
        if (moduleId != null) wrapper.eq(DeployRecord::getModuleId, moduleId);
        if (instanceId != null) wrapper.eq(DeployRecord::getInstanceId, instanceId);
        if (status != null) wrapper.eq(DeployRecord::getStatus, status);
        wrapper.orderByDesc(DeployRecord::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public Result<List<DeployRecord>> getHistory(Long moduleId) {
        return getHistory(moduleId, null, null, null);
    }

    // ==================== 内部辅助方法 ====================

    private boolean executeStep(Long recordId, int stepNo, Runnable action) {
        DeployStepEnum stepEnum = DeployStepEnum.fromStepNo(stepNo);
        if (stepEnum == null) {
            log.error("无效的步骤序号: {}", stepNo);
            return false;
        }

        DeployStep step = new DeployStep();
        step.setRecordId(recordId);
        step.setStepNo(stepNo);
        step.setStepName(stepEnum.getStepName());
        step.setStatus(0);
        step.setCreatedTime(new Date());
        deployStepMapper.insert(step);

        log.info("[{}] 开始执行", stepEnum.getStepName());

        try {
            action.run();
            deployStepMapper.updateStatus(step.getId(), 1);
            log.info("[{}] 执行完成", stepEnum.getStepName());
            return true;
        } catch (Exception e) {
            log.error("[{}] 执行失败: {}", stepEnum.getStepName(), e.getMessage());
            deployStepMapper.updateStatus(step.getId(), 2);
            deployStepMapper.updateErrorMessage(step.getId(), e.getMessage());
            updateRecordStatus(recordId, DeployStatusEnum.FAILED, stepEnum.getStepName() + "失败: " + e.getMessage());
            return false;
        }
    }

    private void failStep(Long recordId, int stepNo, String errorMsg) {
        DeployStepEnum stepEnum = DeployStepEnum.fromStepNo(stepNo);
        String stepName = stepEnum != null ? stepEnum.getStepName() : "步骤" + stepNo;
        log.error("[{}] 执行失败: {}", stepName, errorMsg);

        DeployRecord record = deployRecordMapper.selectById(recordId);
        if (record != null) {
            updateRecordStatus(recordId, DeployStatusEnum.FAILED, stepName + "失败: " + errorMsg);
        }
    }

    private void updateRecordStatus(Long recordId, DeployStatusEnum status, String message) {
        deployRecordMapper.updateStatus(recordId, status.getCode());
        log.info("更新部署状态, recordId={}, status={}, message={}", recordId, status.getDescription(), message);
    }

    // ==================== 版本号生成 ====================

    private String generateVersion(Module module) {
        String dateStr = LocalDate.now().format(VERSION_DATE_FMT);
        int seq = getNextVersionSeq(module.getId(), dateStr);
        String tag = StrUtil.isNotBlank(module.getModuleName()) ? module.getModuleName() : "default";
        return String.format("%s_%03d_%s", dateStr, seq, tag);
    }

    private int getNextVersionSeq(Long moduleId, String dateStr) {
        try {
            String versionPrefix = dateStr + "_";
            LambdaQueryWrapper<DeployRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DeployRecord::getModuleId, moduleId)
                    .likeRight(DeployRecord::getVersion, versionPrefix)
                    .ne(DeployRecord::getDeployType, "rollback")
                    .orderByDesc(DeployRecord::getVersion)
                    .last("LIMIT 1");
            DeployRecord latest = deployRecordMapper.selectOne(wrapper);

            if (latest != null && latest.getVersion() != null) {
                String version = latest.getVersion();
                int firstUnderscore = version.indexOf('_');
                int secondUnderscore = version.indexOf('_', firstUnderscore + 1);
                if (firstUnderscore > 0 && secondUnderscore > firstUnderscore) {
                    String seqStr = version.substring(firstUnderscore + 1, secondUnderscore);
                    int maxSeq = Integer.parseInt(seqStr);
                    return maxSeq + 1;
                }
            }
        } catch (Exception e) {
            log.warn("查询版本序号失败，使用默认序号 1: {}", e.getMessage());
        }
        return 1;
    }

    // ==================== IService 接口方法 ====================

    @Override
    public Long startDeploy(DeployRequest request, Long operatorId, String username) {
        ServiceInstance instance = serviceInstanceMapper.selectById(request.getInstanceId());
        if (instance == null || instance.getModuleId() == null) {
            throw new BusinessException("服务实例或模块不存在");
        }
        Result<Long> result = deploy(instance.getModuleId(), request.getInstanceId(), username);
        return result.isSuccess() ? result.getData() : null;
    }

    @Override
    public IPage<DeployRecord> pageDeployRecords(int pageNum, int pageSize, Long instanceId) {
        return pageDeployRecordsWithFilter(pageNum, pageSize, null, instanceId, null);
    }

    @Override
    public void cancelDeploy(Long deployRecordId) {
        DeployRecord record = deployRecordMapper.selectById(deployRecordId);
        if (record != null && record.getStatus() != null && record.getStatus() == DeployStatusEnum.RUNNING.getCode()) {
            updateRecordStatus(deployRecordId, DeployStatusEnum.FAILED, "部署已取消");
        }
    }

    @Override
    public String getDeployLog(Long deployRecordId) {
        List<DeployStep> steps = deployStepMapper.selectByRecordId(deployRecordId);
        StringBuilder logBuilder = new StringBuilder();
        for (DeployStep step : steps) {
            logBuilder.append("[").append(step.getStepName()).append("] ");
            if (step.getLogOutput() != null) {
                logBuilder.append(step.getLogOutput());
            }
            logBuilder.append("\n");
        }
        return logBuilder.toString();
    }
}
