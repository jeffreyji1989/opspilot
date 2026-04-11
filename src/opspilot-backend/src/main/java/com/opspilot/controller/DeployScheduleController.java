package com.opspilot.controller;

import com.opspilot.common.PageResult;
import com.opspilot.common.Result;
import com.opspilot.entity.DeploySchedule;
import com.opspilot.service.DeployScheduleService;
import com.opspilot.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class DeployScheduleController {

    private final DeployScheduleService deployScheduleService;
    private final OperationLogService operationLogService;

    @GetMapping
    public Result<PageResult<DeploySchedule>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long moduleId) {
        return Result.success(PageResult.of(deployScheduleService.pageSchedules(pageNum, pageSize, moduleId)));
    }

    @PostMapping
    public Result<DeploySchedule> create(@RequestBody DeploySchedule schedule, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        schedule.setCreatorId(userId);
        deployScheduleService.createScheduleWithQuartz(schedule);
        operationLogService.logOperation(userId, "schedule", "CREATE", "SCHEDULE", schedule.getId(),
                schedule.getScheduleName(), "创建定时发版任务", "success", request.getRemoteAddr());
        return Result.success(schedule);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody DeploySchedule schedule, HttpServletRequest request) {
        schedule.setId(id);
        deployScheduleService.updateScheduleWithQuartz(schedule);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "schedule", "UPDATE", "SCHEDULE", id,
                schedule.getScheduleName(), "更新定时发版任务", "success", request.getRemoteAddr());
        return Result.success();
    }

    @PostMapping("/{id}/pause")
    public Result<Void> pause(@PathVariable Long id, HttpServletRequest request) {
        deployScheduleService.pauseSchedule(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "schedule", "PAUSE", "SCHEDULE", id,
                "", "暂停定时任务", "success", request.getRemoteAddr());
        return Result.success();
    }

    @PostMapping("/{id}/resume")
    public Result<Void> resume(@PathVariable Long id, HttpServletRequest request) {
        deployScheduleService.resumeSchedule(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "schedule", "RESUME", "SCHEDULE", id,
                "", "恢复定时任务", "success", request.getRemoteAddr());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        deployScheduleService.deleteSchedule(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "schedule", "DELETE", "SCHEDULE", id,
                "", "删除定时任务", "success", request.getRemoteAddr());
        return Result.success();
    }

    @PostMapping("/{id}/trigger")
    public Result<Void> triggerNow(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        deployScheduleService.triggerNow(id, userId);
        operationLogService.logOperation(userId, "schedule", "TRIGGER", "SCHEDULE", id,
                "", "手动触发定时任务", "success", request.getRemoteAddr());
        return Result.success();
    }
}
