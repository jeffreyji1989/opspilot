package com.opspilot.controller;

import com.opspilot.common.PageResult;
import com.opspilot.common.Result;
import com.opspilot.entity.OperationLog;
import com.opspilot.service.OperationLogService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 操作日志控制器
 *
 * <p>提供操作日志的分页查询、多条件筛选和 CSV 导出 API。</p>
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
     * @param pageNum    页码
     * @param pageSize   每页大小
     * @param userId     用户 ID（可选）
     * @param operation  操作类型（可选）
     * @param targetType 目标类型（可选）
     * @param targetName 目标名称（可选）
     * @param result     操作结果（可选）
     * @param startDate  开始日期（可选）
     * @param endDate    结束日期（可选）
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<OperationLog>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetName,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(PageResult.of(operationLogService.pageLogs(
                pageNum, pageSize, userId, operation, targetType, targetName, result, startDate, endDate)));
    }

    /**
     * 导出操作日志为 CSV
     *
     * @param operation  操作类型筛选
     * @param targetType 目标类型筛选
     * @param response   HTTP 响应
     */
    @GetMapping("/export")
    public void exportCsv(
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetName,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) throws IOException {

        List<OperationLog> logs = operationLogService.exportLogs(
                operation, targetType, targetName, result, startDate, endDate);

        response.setContentType("text/csv;charset=utf-8");
        response.setCharacterEncoding("utf-8");
        String filename = URLEncoder.encode("operation_logs.csv", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);

        try (PrintWriter writer = response.getWriter()) {
            // CSV 头部
            writer.println("ID,操作类型,目标类型,目标名称,详情,IP地址,操作结果,操作时间");
            // 数据行
            for (OperationLog logEntry : logs) {
                writer.printf("%d,%s,%s,%s,%s,%s,%s,%s%n",
                        logEntry.getId(),
                        escapeCsv(logEntry.getOperation()),
                        escapeCsv(logEntry.getTargetType()),
                        escapeCsv(logEntry.getTargetName()),
                        escapeCsv(logEntry.getRequestParams()),
                        escapeCsv(logEntry.getIpAddress()),
                        escapeCsv(logEntry.getResult()),
                        logEntry.getCreateTime() != null ? logEntry.getCreateTime().toString() : "");
            }
        }
    }

    /**
     * CSV 字段转义
     */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
