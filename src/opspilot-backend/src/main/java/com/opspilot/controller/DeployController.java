package com.opspilot.controller;

import com.opspilot.common.PageResult;
import com.opspilot.common.Result;
import com.opspilot.dto.DeployRequest;
import com.opspilot.entity.DeployRecord;
import com.opspilot.entity.DeployStep;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opspilot.mapper.DeployStepMapper;
import com.opspilot.service.DeployService;
import com.opspilot.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deploys")
@RequiredArgsConstructor
public class DeployController {

    private final DeployService deployService;
    private final DeployStepMapper deployStepMapper;
    private final OperationLogService operationLogService;

    @PostMapping
    public Result<Long> deploy(@Valid @RequestBody DeployRequest request, HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        String username = (String) httpRequest.getAttribute("username");
        Long deployId = deployService.startDeploy(request, userId, username);
        operationLogService.logOperation(userId, "deploy", "DEPLOY", "SERVICE", request.getInstanceId(),
                "instance:" + request.getInstanceId(), "发版部署 branch=" + request.getGitBranch(),
                "pending", httpRequest.getRemoteAddr());
        return Result.success(deployId);
    }

    @GetMapping
    public Result<PageResult<DeployRecord>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long instanceId) {
        return Result.success(PageResult.of(deployService.pageDeployRecords(pageNum, pageSize, instanceId)));
    }

    @GetMapping("/{id}")
    public Result<DeployRecord> getById(@PathVariable Long id) {
        return Result.success(deployService.getById(id));
    }

    @GetMapping("/{id}/steps")
    public Result<List<DeployStep>> getSteps(@PathVariable Long id) {
        return Result.success(deployStepMapper.selectList(
                new LambdaQueryWrapper<DeployStep>()
                        .eq(DeployStep::getDeployRecordId, id)
                        .orderByAsc(DeployStep::getStepOrder)));
    }

    @GetMapping("/{id}/log")
    public Result<String> getLog(@PathVariable Long id) {
        return Result.success(deployService.getDeployLog(id));
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, HttpServletRequest request) {
        deployService.cancelDeploy(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "deploy", "CANCEL", "SERVICE", id,
                "", "取消部署", "success", request.getRemoteAddr());
        return Result.success();
    }
}
