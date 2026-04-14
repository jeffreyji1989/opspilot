package com.opspilot.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.opspilot.entity.OperationLog;

/**
 * 操作日志服务接口
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
public interface OperationLogService extends IService<OperationLog> {

    /**
     * 分页查询操作日志
     */
    IPage<OperationLog> pageLogs(int pageNum, int pageSize, Long userId, String operation, String targetType);

    /**
     * 分页查询操作日志（带多条件筛选）
     */
    IPage<OperationLog> pageLogsWithFilter(int pageNum, int pageSize, Long userId, String operation,
                                            String module, String targetType, String startDate, String endDate);

    /**
     * 导出操作日志为 CSV
     */
    String exportCsv(Long userId, String operation, String module, String targetType, String startDate, String endDate);

    /**
     * 记录操作日志
     */
    void logOperation(Long userId, String module, String operation, String targetType,
                      Long targetId, String targetName, String detail, String result, String ipAddress);
}
