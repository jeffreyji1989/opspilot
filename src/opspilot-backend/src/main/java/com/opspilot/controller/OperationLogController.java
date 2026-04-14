package com.opspilot.controller;

import com.opspilot.common.PageResult;
import com.opspilot.common.Result;
import com.opspilot.entity.OperationLog;
import com.opspilot.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 操作日志控制器
 *
 * <p>提供操作日志的分页查询和多条件筛选功能。</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@RestController
@RequestMapping("/api/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    /**
     * 分页查询操作日志（支持多条件筛选）
     *
     * @param pageNum     页码
     * @param pageSize    每页大小
     * @param userId      操作人ID（可选）
     * @param operation   操作类型（可选）
     * @param module      操作模块（可选）
     * @param targetType  目标类型（可选）
     * @param startDate   开始日期（可选，格式 yyyy-MM-dd）
     * @param endDate     结束日期（可选，格式 yyyy-MM-dd）
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<OperationLog>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(PageResult.of(
                operationLogService.pageLogsWithFilter(pageNum, pageSize, userId, operation, module, targetType, startDate, endDate)));
    }

    /**
     * 导出操作日志为 CSV
     *
     * @param userId      操作人ID（可选）
     * @param operation   操作类型（可选）
     * @param module      操作模块（可选）
     * @param targetType  目标类型（可选）
     * @param startDate   开始日期（可选）
     * @param endDate     结束日期（可选）
     * @return CSV 内容
     */
    @GetMapping("/export")
    public Result<String> export(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(operationLogService.exportCsv(userId, operation, module, targetType, startDate, endDate));
    }
}
