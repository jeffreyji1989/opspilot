package com.opspilot.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.json.JSONUtil;
import com.opspilot.common.Result;
import com.opspilot.common.SshManager;
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
import org.springframework.stereotype.Service;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 发版部署服务实现类
 *
 * <p>提供完整的发版部署生命周期管理，包括：</p>
 * <ul>
 *   <li>7 步标准化发版流程：拉取代码 → 编译构建 → 打包产物 → 上传至服务器 → 切换版本 → 重启服务 → 健康检查</li>
 *   <li>部署进度实时查询</li>
 *   <li>版本回退（软链回退 + 服务重启）</li>
 *   <li>部署历史记录</li>
 * </ul>
 *
 * <p>所有异步操作通过 {@code deployTaskExecutor} 线程池执行，避免 {@code new Thread()} 导致的线程泄漏。</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@Service
public class DeployServiceImpl implements DeployService {

    private static final Logger log = LoggerFactory.getLogger(DeployServiceImpl.class);

    /** 本地构建产物存放根目录 */
    private static final String LOCAL_BUILD_DIR = "/tmp/opspilot/builds";

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
    private org.springframework.core.task.TaskExecutor deployTaskExecutor;

    // ==================== 核心部署接口 ====================

    /**
     * 执行发版部署
     *
     * <p>创建部署记录，并在异步线程中执行 7 步标准化发版流程。
     * 调用方通过 {@link #getProgress(Long)} 查询实时进度。</p>
     *
     * @param moduleId   模块 ID
     * @param instanceId 实例 ID
     * @param operator   操作人
     * @return 部署记录 ID
     */
    @Override
    public Result<Long> deploy(Long moduleId, Long instanceId, String operator) {
        log.info("发起发版部署, moduleId={}, instanceId={}, operator={}", moduleId, instanceId, operator);

        // 1. 参数校验
        if (moduleId == null || instanceId == null) {
            log.warn("发版部署参数不完整, moduleId={}, instanceId={}", moduleId, instanceId);
            return Result.error("模块和实例不能为空");
        }

        // 2. 查询模块信息
        Module module = moduleMapper.selectById(moduleId);
        if (module == null) {
            log.warn("模块不存在, moduleId={}", moduleId);
            return Result.error("模块不存在");
        }

        // 3. 查询实例信息
        ServiceInstance instance = serviceInstanceMapper.selectById(instanceId);
        if (instance == null) {
            log.warn("实例不存在, instanceId={}", instanceId);
            return Result.error("实例不存在");
        }

        // 4. 检查实例是否正在部署中
        if (instance.getProcessStatus() != null && instance.getProcessStatus() == DeployStatusEnum.RUNNING.getCode()) {
            log.warn("实例正在部署中, instanceId={}", instanceId);
            return Result.error("实例正在部署中，请稍后再试");
        }

        // 5. 创建部署记录
        DeployRecord record = new DeployRecord();
        record.setModuleId(moduleId);
        record.setInstanceId(instanceId);
        record.setVersion("v" + System.currentTimeMillis());
        record.setDeployType("normal");
        record.setStatus(DeployStatusEnum.PENDING.getCode());
        record.setOperator(operator);
        record.setCreatedTime(new Date());
        deployRecordMapper.insert(record);

        Long recordId = record.getId();
        log.info("创建部署记录成功, recordId={}", recordId);

        // 6. 异步执行部署流程
        final Long finalRecordId = recordId;
        deployTaskExecutor.execute(() -> executeDeploy(finalRecordId, module, instance, operator));

        return Result.success(recordId);
    }

    // ==================== 7 步发版流程 ====================

    /**
     * 执行 7 步标准化发版流程
     *
     * <p>流程：拉取代码 → 编译构建 → 打包产物 → 上传至服务器 → 切换版本 → 重启服务 → 健康检查</p>
     *
     * @param recordId 部署记录 ID
     * @param module   模块信息
     * @param instance 实例信息
     * @param operator 操作人
     */
    private void executeDeploy(Long recordId, Module module, ServiceInstance instance, String operator) {
        log.info("开始执行发版流程, recordId={}, module={}, instance={}", recordId, module.getModuleName(), instance.getInstanceName());

        // 更新实例状态为部署中
        serviceInstanceMapper.updateProcessStatus(instance.getId(), DeployStatusEnum.RUNNING.getCode());

        // 查询服务器信息
        Server server = serverMapper.selectById(instance.getServerId());
        if (server == null) {
            log.error("服务器不存在, serverId={}", instance.getServerId());
            updateRecordStatus(recordId, DeployStatusEnum.FAILED, "服务器不存在");
            serviceInstanceMapper.updateProcessStatus(instance.getId(), DeployStatusEnum.FAILED.getCode());
            return;
        }

        // 生成本地构建目录
        String buildBaseDir = LOCAL_BUILD_DIR + "/" + recordId;
        String projectDir = buildBaseDir + "/" + module.getModuleName();
        FileUtil.mkdir(buildBaseDir);

        boolean success = true;

        try {
            // Step 1: 拉取代码
            success = executeStep(recordId, 1, () -> pullCode(module, projectDir));
            if (!success) return;

            // Step 2: 编译构建
            success = executeStep(recordId, 2, () -> buildProject(module, projectDir));
            if (!success) return;

            // Step 3: 打包产物
            String artifactPath = packageArtifact(module, projectDir, buildBaseDir);
            if (StrUtil.isBlank(artifactPath)) {
                failStep(recordId, 3, "打包产物失败，未找到构建产物");
                return;
            }

            // Step 4: 上传至服务器
            success = executeStep(recordId, 4, () -> uploadToServer(server, artifactPath, instance.getDeployPath()));
            if (!success) return;

            // Step 5: 切换版本（软链切换）
            success = executeStep(recordId, 5, () -> switchVersion(server, instance, artifactPath));
            if (!success) return;

            // Step 6: 重启服务
            success = executeStep(recordId, 6, () -> restartService(server, instance));
            if (!success) return;

            // Step 7: 健康检查
            success = executeStep(recordId, 7, () -> healthCheck(server, instance));
            if (!success) return;

            // 全部成功
            updateRecordStatus(recordId, DeployStatusEnum.SUCCESS, "部署成功");
            serviceInstanceMapper.updateProcessStatus(instance.getId(), DeployStatusEnum.SUCCESS.getCode());
            serviceInstanceMapper.updateCurrentVersion(instance.getId(), record.getVersion());
            log.info("发版部署成功, recordId={}, version={}", recordId, record.getVersion());

        } catch (Exception e) {
            log.error("发版部署异常, recordId={}", recordId, e);
            updateRecordStatus(recordId, DeployStatusEnum.FAILED, "部署异常: " + e.getMessage());
            serviceInstanceMapper.updateProcessStatus(instance.getId(), DeployStatusEnum.FAILED.getCode());
        } finally {
            // 清理本地构建目录
            FileUtil.del(buildBaseDir);
            log.info("清理本地构建目录, dir={}", buildBaseDir);
        }
    }

    // ==================== 7 步具体实现 ====================

    /**
     * 步骤1: 拉取代码
     *
     * <p>从 Git 仓库拉取代码到本地构建目录。支持 SSH key 认证。</p>
     *
     * @param module    模块信息（包含仓库地址、分支、代码路径）
     * @param projectDir 本地项目目录
     */
    private void pullCode(Module module, String projectDir) {
        String repoUrl = module.getRepoUrl();
        String branch = StrUtil.isNotBlank(module.getRepoBranch()) ? module.getRepoBranch() : "main";

        // 如果已存在 .git 目录，执行 pull 更新
        if (FileUtil.exist(projectDir + "/.git")) {
            String pullCmd = String.format("cd %s && git pull origin %s", projectDir, branch);
            String pullResult = SshManager.executeCommand(pullCmd, null, 120);
            if (StrUtil.isBlank(pullResult) || pullResult.contains("Already up to date") || pullResult.contains("Updating")) {
                log.info("代码更新成功, branch={}", branch);
            } else {
                log.error("代码更新失败, result={}", pullResult);
                throw new RuntimeException("代码更新失败: " + pullResult);
            }
        } else {
            // 首次克隆
            FileUtil.mkdir(projectDir);
            String cloneCmd = String.format("git clone -b %s %s %s", branch, repoUrl, projectDir);
            String cloneResult = SshManager.executeCommand(cloneCmd, null, 180);
            if (cloneResult.contains("fatal") || cloneResult.contains("error")) {
                log.error("代码克隆失败, repoUrl={}, result={}", repoUrl, cloneResult);
                throw new RuntimeException("代码克隆失败: " + cloneResult);
            }
            log.info("代码克隆成功, repoUrl={}, branch={}", repoUrl, branch);
        }
    }

    /**
     * 步骤2: 编译构建
     *
     * <p>根据模块类型执行对应的构建命令（Maven/Node.js/Python 等）。</p>
     *
     * @param module     模块信息（包含构建命令）
     * @param projectDir 本地项目目录
     */
    private void buildProject(Module module, String projectDir) {
        String buildCmd = module.getBuildCommand();
        if (StrUtil.isBlank(buildCmd)) {
            // 默认使用 Maven 构建
            buildCmd = "mvn clean package -DskipTests";
        }

        String fullCmd = String.format("cd %s && %s", projectDir, buildCmd);
        String buildResult = SshManager.executeCommand(fullCmd, null, 300);

        if (buildResult.contains("BUILD FAILURE") || buildResult.contains("ERROR") || buildResult.contains("error")) {
            log.error("编译构建失败, module={}, result={}", module.getModuleName(), buildResult);
            throw new RuntimeException("编译构建失败: " + buildResult);
        }
        log.info("编译构建成功, module={}", module.getModuleName());
    }

    /**
     * 步骤3: 打包产物
     *
     * <p>查找构建产物（JAR/WAR 文件），如果找到多个则打包为 ZIP。</p>
     *
     * @param module     模块信息
     * @param projectDir 本地项目目录
     * @param buildBaseDir 构建基础目录
     * @return 打包后的产物路径，未找到则返回 null
     */
    private String packageArtifact(Module module, String projectDir, String buildBaseDir) {
        String artifactPath = module.getArtifactPath();
        String targetDir = projectDir + "/target";

        // 优先使用模块配置的产物路径
        if (StrUtil.isNotBlank(artifactPath) && FileUtil.exist(artifactPath)) {
            log.info("使用指定产物路径: {}", artifactPath);
            return artifactPath;
        }

        // 从 target 目录查找 JAR/WAR
        File targetDirFile = new File(targetDir);
        if (!targetDirFile.exists()) {
            log.error("构建产物目录不存在: {}", targetDir);
            return null;
        }

        File[] artifacts = targetDirFile.listFiles(f -> f.getName().endsWith(".jar") || f.getName().endsWith(".war"));
        if (artifacts == null || artifacts.length == 0) {
            log.error("未找到构建产物, targetDir={}", targetDir);
            return null;
        }

        if (artifacts.length == 1) {
            String path = artifacts[0].getAbsolutePath();
            log.info("找到构建产物: {}", path);
            return path;
        }

        // 多个产物，打包为 ZIP
        String zipPath = buildBaseDir + "/" + module.getModuleName() + "-" + System.currentTimeMillis() + ".zip";
        ZipUtil.zip(targetDir, zipPath);
        log.info("多个产物已打包为 ZIP: {}", zipPath);
        return zipPath;
    }

    /**
     * 步骤4: 上传至服务器
     *
     * <p>将构建产物上传到目标服务器的部署目录。</p>
     *
     * @param server      服务器信息
     * @param artifactPath 本地产物路径
     * @param deployPath   远程部署目录
     */
    private void uploadToServer(Server server, String artifactPath, String deployPath) {
        if (StrUtil.isBlank(artifactPath) || !FileUtil.exist(artifactPath)) {
            throw new RuntimeException("产物文件不存在: " + artifactPath);
        }

        String fileName = FileUtil.getName(artifactPath);
        String remotePath = deployPath + "/uploads/" + fileName;

        // 确保远程目录存在
        SshManager.executeCommand(
                String.format("mkdir -p %s/uploads", deployPath),
                server, 30);

        // 上传文件
        boolean uploaded = SshManager.uploadFile(artifactPath, remotePath, server);
        if (!uploaded) {
            throw new RuntimeException("文件上传失败: " + artifactPath + " -> " + remotePath);
        }
        log.info("文件上传成功, remotePath={}", remotePath);
    }

    /**
     * 步骤5: 切换版本（软链切换）
     *
     * <p>通过软链切换实现快速版本切换，支持一键回退。
     * 目录结构：deployPath/current → versions/v{timestamp}/</p>
     *
     * @param server   服务器信息
     * @param instance 实例信息
     * @param artifactPath 上传的产物路径
     */
    private void switchVersion(Server server, ServiceInstance instance, String artifactPath) {
        String deployPath = instance.getDeployPath();
        String versionDir = deployPath + "/versions/v" + System.currentTimeMillis();
        String currentLink = deployPath + "/current";

        String fileName = FileUtil.getName(artifactPath);

        // 创建版本目录
        String switchCmd = String.format(
                "mkdir -p %s && cp %s/uploads/%s %s/%s",
                versionDir, deployPath, fileName, versionDir, fileName);
        String result = SshManager.executeCommand(switchCmd, server, 60);
        if (result.contains("No space left") || result.contains("Permission denied")) {
            throw new RuntimeException("切换版本失败: " + result);
        }

        // 切换软链
        String linkCmd = String.format("ln -sfn %s %s", versionDir, currentLink);
        SshManager.executeCommand(linkCmd, server, 30);

        log.info("版本切换成功, versionDir={}", versionDir);
    }

    /**
     * 步骤6: 重启服务
     *
     * <p>停止旧进程，启动新版本服务。启动命令从实例配置中获取。</p>
     *
     * @param server   服务器信息
     * @param instance 实例信息
     */
    private void restartService(Server server, ServiceInstance instance) {
        String deployPath = instance.getDeployPath();
        String currentLink = deployPath + "/current";
        String startCommand = instance.getStartCommand();
        String jvmOptions = instance.getJvmOptions();

        // 停止旧进程
        if (instance.getPid() != null) {
            String stopCmd = String.format("kill -15 %d", instance.getPid());
            SshManager.executeCommand(stopCmd, server, 15);
            log.info("已发送停止信号, pid={}", instance.getPid());
        }

        // 启动新版本
        if (StrUtil.isNotBlank(startCommand)) {
            String fullStartCmd = String.format("cd %s && %s &", currentLink, startCommand);
            SshManager.executeCommand(fullStartCmd, server, 30);
        } else {
            // 默认启动 JAR
            String jarName = FileUtil.getName(FileUtil.loopFiles(new File("/tmp"), f -> f.getName().endsWith(".jar")));
            String defaultCmd;
            if (StrUtil.isNotBlank(jvmOptions)) {
                defaultCmd = String.format("cd %s && nohup java %s -jar *.jar > app.log 2>&1 &", currentLink, jvmOptions);
            } else {
                defaultCmd = String.format("cd %s && nohup java -jar *.jar > app.log 2>&1 &", currentLink);
            }
            SshManager.executeCommand(defaultCmd, server, 30);
        }

        // 等待启动
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("服务重启完成");
    }

    /**
     * 步骤7: 健康检查
     *
     * <p>检查服务进程是否存活，如果配置了健康检查路径则检查 HTTP 响应。</p>
     *
     * @param server   服务器信息
     * @param instance 实例信息
     */
    private void healthCheck(Server server, ServiceInstance instance) {
        // 检查进程
        String healthCheckPath = instance.getHealthCheckPath();
        Integer listenPort = instance.getListenPort();

        if (StrUtil.isNotBlank(healthCheckPath) && listenPort != null) {
            // HTTP 健康检查
            String checkCmd = String.format("curl -s -o /dev/null -w '%%{http_code}' --max-time 10 http://localhost:%d%s",
                    listenPort, healthCheckPath);
            String statusCode = SshManager.executeCommand(checkCmd, server, 15);
            if (!"200".equals(statusCode.trim())) {
                throw new RuntimeException("健康检查失败, HTTP " + statusCode);
            }
            log.info("HTTP 健康检查通过, port={}, path={}", listenPort, healthCheckPath);
        } else {
            // 进程检查
            String processCmd = String.format("pgrep -f 'java.*%s'", instance.getDeployPath());
            String pidResult = SshManager.executeCommand(processCmd, server, 10);
            if (StrUtil.isBlank(pidResult)) {
                throw new RuntimeException("进程检查失败，服务未启动");
            }
            log.info("进程检查通过, pid={}", pidResult.trim());
        }
    }

    // ==================== 版本回退 ====================

    /**
     * 执行版本回退
     *
     * <p>回退到上一个稳定版本。通过软链切换实现，不影响历史版本目录。
     * 回退流程：查找上一版本 → 切换软链 → 重启服务 → 健康检查</p>
     *
     * @param instanceId 实例 ID
     * @param operator   操作人
     * @return 回退记录 ID
     */
    @Override
    public Result<Long> rollback(Long instanceId, String operator) {
        log.info("执行版本回退, instanceId={}, operator={}", instanceId, operator);

        ServiceInstance instance = serviceInstanceMapper.selectById(instanceId);
        if (instance == null) {
            return Result.error("实例不存在");
        }

        Server server = serverMapper.selectById(instance.getServerId());
        if (server == null) {
            return Result.error("服务器不存在");
        }

        // 创建回退记录
        DeployRecord record = new DeployRecord();
        record.setModuleId(instance.getModuleId());
        record.setInstanceId(instanceId);
        record.setVersion("rollback");
        record.setDeployType("rollback");
        record.setStatus(DeployStatusEnum.ROLLING_BACK.getCode());
        record.setOperator(operator);
        record.setCreatedTime(new Date());
        deployRecordMapper.insert(record);

        final Long recordId = record.getId();

        deployTaskExecutor.execute(() -> executeRollback(recordId, server, instance));

        return Result.success(recordId);
    }

    /**
     * 执行回退流程
     *
     * @param recordId 回退记录 ID
     * @param server   服务器信息
     * @param instance 实例信息
     */
    private void executeRollback(Long recordId, Server server, ServiceInstance instance) {
        String deployPath = instance.getDeployPath();
        String versionsDir = deployPath + "/versions";

        try {
            // 查找上一个版本目录
            String listCmd = String.format("ls -lt %s | grep '^d' | head -2 | tail -1 | awk '{print $NF}'", versionsDir);
            String prevVersion = SshManager.executeCommand(listCmd, server, 10);
            prevVersion = StrUtil.isNotBlank(prevVersion) ? prevVersion.trim() : null;

            if (StrUtil.isBlank(prevVersion)) {
                updateRecordStatus(recordId, DeployStatusEnum.FAILED, "无历史版本可回退");
                return;
            }

            String prevVersionDir = versionsDir + "/" + prevVersion;
            String currentLink = deployPath + "/current";

            // 切换软链到上一版本
            String linkCmd = String.format("ln -sfn %s %s", prevVersionDir, currentLink);
            SshManager.executeCommand(linkCmd, server, 10);

            // 重启服务
            String stopCmd = String.format("pkill -f 'java.*%s'", deployPath);
            SshManager.executeCommand(stopCmd, server, 15);
            Thread.sleep(3000);

            String startCmd = String.format("cd %s && nohup java -jar *.jar > app.log 2>&1 &", currentLink);
            SshManager.executeCommand(startCmd, server, 15);
            Thread.sleep(5000);

            // 健康检查
            if (instance.getListenPort() != null && StrUtil.isNotBlank(instance.getHealthCheckPath())) {
                String checkCmd = String.format("curl -s -o /dev/null -w '%%{http_code}' --max-time 10 http://localhost:%d%s",
                        instance.getListenPort(), instance.getHealthCheckPath());
                String statusCode = SshManager.executeCommand(checkCmd, server, 15);
                if (!"200".equals(statusCode.trim())) {
                    updateRecordStatus(recordId, DeployStatusEnum.FAILED, "回退后健康检查失败");
                    return;
                }
            }

            updateRecordStatus(recordId, DeployStatusEnum.ROLLED_BACK, "回退成功，版本: " + prevVersion);
            log.info("版本回退成功, version={}", prevVersion);

        } catch (Exception e) {
            log.error("版本回退异常, recordId={}", recordId, e);
            updateRecordStatus(recordId, DeployStatusEnum.FAILED, "回退异常: " + e.getMessage());
        }
    }

    // ==================== 查询接口 ====================

    /**
     * 查询发版进度
     *
     * @param recordId 部署记录 ID
     * @return 部署进度信息，包含当前步骤、状态和各步骤详情
     */
    @Override
    public Result<DeployRecord> getProgress(Long recordId) {
        DeployRecord record = deployRecordMapper.selectById(recordId);
        if (record == null) {
            return Result.error("部署记录不存在");
        }
        List<DeployStep> steps = deployStepMapper.selectByRecordId(recordId);
        return Result.success(record);
    }

    /**
     * 查询发版历史记录
     *
     * @param moduleId 模块 ID
     * @return 部署历史列表
     */
    @Override
    public Result<List<DeployRecord>> getHistory(Long moduleId) {
        List<DeployRecord> records = deployRecordMapper.selectByModuleId(moduleId);
        return Result.success(records);
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 执行单步部署操作
     *
     * <p>封装步骤的开始、执行和结果上报逻辑。执行成功后自动开始下一步。</p>
     *
     * @param recordId 部署记录 ID
     * @param stepNo   步骤序号（1-7）
     * @param action   具体执行逻辑
     * @return 是否执行成功
     */
    private boolean executeStep(Long recordId, int stepNo, Runnable action) {
        DeployStepEnum stepEnum = DeployStepEnum.fromStepNo(stepNo);
        if (stepEnum == null) {
            log.error("无效的步骤序号: {}", stepNo);
            return false;
        }

        // 记录步骤开始
        DeployStep step = new DeployStep();
        step.setRecordId(recordId);
        step.setStepNo(stepNo);
        step.setStepName(stepEnum.getStepName());
        step.setStatus(0); // 执行中
        step.setCreatedTime(new Date());
        deployStepMapper.insert(step);

        log.info("[{}] 开始执行", stepEnum.getStepName());

        try {
            action.run();
            // 标记成功
            deployStepMapper.updateStatus(step.getId(), 1); // 1=成功
            log.info("[{}] 执行完成", stepEnum.getStepName());
            return true;
        } catch (Exception e) {
            log.error("[{}] 执行失败: {}", stepEnum.getStepName(), e.getMessage());
            deployStepMapper.updateStatus(step.getId(), 2); // 2=失败
            updateRecordStatus(recordId, DeployStatusEnum.FAILED, stepEnum.getStepName() + "失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 标记步骤失败（不抛异常场景）
     *
     * @param recordId 部署记录 ID
     * @param stepNo   步骤序号
     * @param errorMsg 错误信息
     */
    private void failStep(Long recordId, int stepNo, String errorMsg) {
        DeployStepEnum stepEnum = DeployStepEnum.fromStepNo(stepNo);
        String stepName = stepEnum != null ? stepEnum.getStepName() : "步骤" + stepNo;
        log.error("[{}] 执行失败: {}", stepName, errorMsg);

        DeployRecord record = deployRecordMapper.selectById(recordId);
        if (record != null) {
            updateRecordStatus(recordId, DeployStatusEnum.FAILED, stepName + "失败: " + errorMsg);
        }
    }

    /**
     * 更新部署记录状态
     *
     * @param recordId  部署记录 ID
     * @param status    目标状态枚举
     * @param message   状态描述消息
     */
    private void updateRecordStatus(Long recordId, DeployStatusEnum status, String message) {
        deployRecordMapper.updateStatus(recordId, status.getCode());
        log.info("更新部署状态, recordId={}, status={}, message={}", recordId, status.getDescription(), message);
    }
}
