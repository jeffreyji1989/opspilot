package com.opspilot.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.opspilot.entity.OperationLog;

import java.util.List;

/**
 * 操作日志服务接口
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
public interface OperationLogService extends IService<OperationLog> {
    IPage<OperationLog> pageLogs(int pageNum, int pageSize, Long userId, String operation, String targetType);

    IPage<OperationLog> pageLogs(int pageNum, int pageSize, Long userId, String operation,
                                 String targetType, String targetName, String result,
                                 String startDate, String endDate);

    void logOperation(Long userId, String module, String operation, String targetType,
                      Long targetId, String targetName, String detail, String result, String ipAddress);

    /**
     * 导出操作日志（支持多条件筛选）
     */
    List<OperationLog> exportLogs(String operation, String targetType, String targetName,
                                  String result, String startDate, String endDate);
}
