package com.opspilot.controller;

import com.opspilot.common.Result;
import com.opspilot.entity.DeployRecord;
import com.opspilot.entity.DeployStep;
import com.opspilot.mapper.DeployStepMapper;
import com.opspilot.service.DeployService;
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

    /**
     * 发起发版部署
     *
     * @param request 包含 moduleId、instanceId、operator 的请求体
     * @return 部署记录 ID
     */
    @PostMapping("/execute")
    public Result<Long> executeDeploy(@RequestBody Map<String, Object> request) {
        Long moduleId = Long.valueOf(request.get("moduleId").toString());
        Long instanceId = Long.valueOf(request.get("instanceId").toString());
        String operator = (String) request.getOrDefault("operator", "system");
        return deployService.deploy(moduleId, instanceId, operator);
    }

    /**
     * 查询发版进度
     *
     * @param recordId 部署记录 ID
     * @return 部署进度信息，包含各步骤详情
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
     * 版本回退
     *
     * <p>将指定实例回退到上一个稳定版本。</p>
     *
     * @param instanceId 实例 ID
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
}
