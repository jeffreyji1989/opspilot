package com.opspilot.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.opspilot.common.PageResult;
import com.opspilot.common.Result;
import com.opspilot.entity.DeployRecord;
import com.opspilot.entity.DeployStep;
import com.opspilot.entity.Module;
import com.opspilot.entity.Server;
import com.opspilot.entity.ServiceInstance;
import com.opspilot.mapper.DeployRecordMapper;
import com.opspilot.mapper.DeployStepMapper;
import com.opspilot.mapper.ModuleMapper;
import com.opspilot.mapper.ServerMapper;
import com.opspilot.mapper.ServiceInstanceMapper;
import com.opspilot.service.DeployService;
import com.opspilot.service.GitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 发版部署控制器
 *
 * <p>提供发版部署的 RESTful API，包括发起部署、查询进度、版本回退和查询历史记录。</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@RestController
@RequestMapping("/api/deploy")
public class DeployController {

    private static final Logger log = LoggerFactory.getLogger(DeployController.class);

    @Autowired
    private DeployService deployService;

    @Autowired
    private DeployStepMapper deployStepMapper;

    @Autowired
    private ModuleMapper moduleMapper;

    @Autowired
    private ServerMapper serverMapper;

    @Autowired
    private ServiceInstanceMapper serviceInstanceMapper;

    @Autowired
    private GitService gitService;

    @Autowired
    private DeployRecordMapper deployRecordMapper;

    /**
     * 发起发版部署
     *
     * @param request 包含 moduleId、instanceId、gitBranch、gitCommit 的请求体
     * @return 部署记录 ID
     */
    @PostMapping("/execute")
    public Result<Long> executeDeploy(@RequestBody Map<String, Object> request) {
        if (request == null) {
            return Result.error("请求参数不能为空");
        }
        Object moduleIdObj = request.get("moduleId");
        Object instanceIdObj = request.get("instanceId");
        if (moduleIdObj == null || instanceIdObj == null) {
            return Result.error("moduleId 和 instanceId 不能为空");
        }

        Long moduleId = Long.valueOf(moduleIdObj.toString());
        Long instanceId = Long.valueOf(instanceIdObj.toString());
        String gitBranch = (String) request.getOrDefault("gitBranch", "main");
        String gitCommit = (String) request.get("gitCommit");
        String operator = (String) request.getOrDefault("operator", "system");

        Long recordIdResult = deployService.deploy(moduleId, instanceId, operator).getData();
        if (recordIdResult != null) {
            DeployRecord record = new DeployRecord();
            record.setId(recordIdResult);
            record.setGitBranch(gitBranch);
            record.setGitCommit(gitCommit);
            deployRecordMapper.updateById(record);
            log.info("已更新部署记录 {} 的 gitBranch={} gitCommit={}", recordIdResult, gitBranch, gitCommit);
        }

        return Result.success(recordIdResult);
    }

    /**
     * 查询发版进度（含步骤详情）
     *
     * @param recordId 部署记录 ID
     * @return 部署进度信息，包含各步骤详情
     */
    @GetMapping("/progress/{recordId}")
    public Result<Map<String, Object>> getProgress(@PathVariable Long recordId) {
        return deployService.getProgress(recordId);
    }

    /**
     * 版本回退
     *
     * <p>将指定实例回退到上一个稳定版本，或指定目标版本。</p>
     *
     * @param instanceId 实例 ID
     * @param operator   操作人
     * @return 回退记录 ID
     */
    @PostMapping("/rollback/{instanceId}")
    public Result<Long> rollback(@PathVariable Long instanceId,
                                 @RequestParam(defaultValue = "system") String operator) {
        return deployService.rollback(instanceId, operator);
    }

    /**
     * 查询发版历史记录
     *
     * @param moduleId 模块 ID
     * @return 部署历史列表
     */
    @GetMapping("/history/{moduleId}")
    public Result<List<DeployRecord>> getHistory(@PathVariable Long moduleId) {
        return deployService.getHistory(moduleId);
    }

    /**
     * 分页查询发版历史记录
     *
     * @param pageNum    页码
     * @param pageSize   每页大小
     * @param instanceId 实例 ID（可选）
     * @param status     状态筛选（可选）
     * @return 分页结果
     */
    @GetMapping("/history")
    public Result<PageResult<DeployRecord>> pageHistory(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long instanceId,
            @RequestParam(required = false) Integer status) {
        IPage<DeployRecord> page = deployService.pageDeployHistory(pageNum, pageSize, instanceId, status);
        return Result.success(PageResult.of(page));
    }

    /**
     * 拉取 Git 分支列表
     *
     * <p>根据模块 ID 获取其关联的 Git 仓库的分支列表。</p>
     *
     * @param moduleId 模块 ID
     * @return 分支名称列表
     */
    @GetMapping("/branches/{moduleId}")
    public Result<List<String>> listBranches(@PathVariable Long moduleId) {
        Module module = moduleMapper.selectById(moduleId);
        if (module == null) {
            return Result.error("模块不存在");
        }
        if (module.getRepoUrl() == null) {
            return Result.error("模块未配置 Git 仓库地址");
        }

        // 尝试通过服务器获取分支列表
        ServiceInstance instance = serviceInstanceMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ServiceInstance>()
                        .eq(ServiceInstance::getModuleId, moduleId)
                        .last("LIMIT 1"));

        List<String> branches;
        if (instance != null && instance.getServerId() != null) {
            Server server = serverMapper.selectById(instance.getServerId());
            branches = gitService.listBranchesFromServer(module, server);
        } else {
            branches = gitService.listBranches(module);
        }

        return Result.success(branches);
    }
}
