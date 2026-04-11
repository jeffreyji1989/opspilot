package com.opspilot.controller;

import com.opspilot.common.PageResult;
import com.opspilot.common.Result;
import com.opspilot.entity.OperationLog;
import com.opspilot.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    @GetMapping
    public Result<PageResult<OperationLog>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String targetType) {
        return Result.success(PageResult.of(operationLogService.pageLogs(pageNum, pageSize, userId, operation, targetType)));
    }
}
