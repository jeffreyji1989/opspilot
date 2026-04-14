package com.opspilot.controller;

import cn.hutool.core.util.StrUtil;
import com.opspilot.common.PageResult;
import com.opspilot.common.Result;
import com.opspilot.common.SshManager;
import com.opspilot.entity.DeployRecord;
import com.opspilot.entity.DeployStep;
import com.opspilot.entity.Module;
import com.opspilot.entity.Server;
import com.opspilot.entity.ServiceInstance;
import com.opspilot.mapper.DeployStepMapper;
import com.opspilot.mapper.ModuleMapper;
import com.opspilot.mapper.ServerMapper;
import com.opspilot.mapper.ServiceInstanceMapper;
import com.opspilot.service.DeployService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
    private SshManager sshManager;

    /**
     * 发起发版部署
     */
    @PostMapping("/execute")
    public Result<Long> executeDeploy(@RequestBody Map<String, Object> request) {
        Long moduleId = Long.valueOf(request.get("moduleId").toString());
        Long instanceId = Long.valueOf(request.get("instanceId").toString());
        String operator = (String) request.getOrDefault("operator", "system");
        return deployService.deploy(moduleId, instanceId, operator);
    }

    /**
     * 查询发版进度（含步骤详情）
     */
    @GetMapping("/progress/{recordId}")
    public Result<Map<String, Object>> getProgress(@PathVariable Long recordId) {
        Result<DeployRecord> recordResult = deployService.getProgress(recordId);
        if (!recordResult.isSuccess()) {
            return Result.error(recordResult.getMessage());
        }

        DeployRecord record = recordResult.getData();
        List<DeployStep> steps = deployStepMapper.selectByRecordId(recordId);

        Map<String, Object> result = new HashMap<>();
        result.put("record", record);
        result.put("steps", steps);
        return Result.success(result);
    }

    /**
     * 版本回退（回退到上一个版本）
     */
    @PostMapping("/rollback/{instanceId}")
    public Result<Long> rollback(@PathVariable Long instanceId,
                                 @RequestParam(defaultValue = "system") String operator) {
        return deployService.rollback(instanceId, operator);
    }

    /**
     * 回退到指定版本
     */
    @PostMapping("/rollback/{instanceId}/to")
    public Result<Long> rollbackTo(@PathVariable Long instanceId,
                                    @RequestBody Map<String, String> request,
                                    @RequestParam(defaultValue = "system") String operator) {
        String targetVersion = request.get("targetVersion");
        if (StrUtil.isBlank(targetVersion)) {
            return Result.error("目标版本不能为空");
        }
        return deployService.rollbackToVersion(instanceId, targetVersion, operator);
    }

    /**
     * 查询发版历史记录
     */
    @GetMapping("/history/{moduleId}")
    public Result<List<DeployRecord>> getHistory(@PathVariable Long moduleId,
                                                  @RequestParam(required = false) Integer status,
                                                  @RequestParam(required = false) String startDate,
                                                  @RequestParam(required = false) String endDate) {
        return deployService.getHistory(moduleId, status, startDate, endDate);
    }

    /**
     * 分页查询发版历史
     */
    @GetMapping("/history")
    public Result<PageResult<DeployRecord>> pageHistory(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long moduleId,
            @RequestParam(required = false) Long instanceId,
            @RequestParam(required = false) Integer status) {
        IPage<DeployRecord> page = deployService.pageDeployRecordsWithFilter(pageNum, pageSize, moduleId, instanceId, status);
        return Result.success(PageResult.of(page));
    }

    /**
     * 拉取远程 Git 分支列表
     */
    @GetMapping("/branches/{moduleId}")
    public Result<List<String>> getBranches(@PathVariable Long moduleId) {
        Module module = moduleMapper.selectById(moduleId);
        if (module == null) {
            return Result.error("模块不存在");
        }

        // 查找该模块关联的服务器（通过服务实例）
        ServiceInstance instance = serviceInstanceMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ServiceInstance>()
                        .eq(ServiceInstance::getModuleId, moduleId)
                        .last("LIMIT 1"));
        if (instance == null) {
            return Result.error("该模块暂无服务实例，无法拉取分支");
        }

        Server server = serverMapper.selectById(instance.getServerId());
        if (server == null) {
            return Result.error("服务器不存在");
        }

        try {
            // 在服务器上执行 git ls-remote 获取远程分支
            String cmd = String.format(
                    "git ls-remote --heads %s 2>/dev/null | awk -F'/' '{print $NF}' | sort",
                    module.getRepoUrl());
            String output = sshManager.executeCommand(cmd, server, 30);
            List<String> branches = new ArrayList<>();
            for (String line : output.split("\n")) {
                String branch = line.trim();
                if (!branch.isEmpty()) {
                    branches.add(branch);
                }
            }
            log.info("获取远程分支列表, module={}, branches={}", module.getModuleName(), branches);
            return Result.success(branches);
        } catch (Exception e) {
            log.error("获取远程分支失败: {}", e.getMessage());
            return Result.error("获取远程分支失败: " + e.getMessage());
        }
    }
}
