package com.opspilot.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.opspilot.entity.OperationLog;

public interface OperationLogService extends IService<OperationLog> {
    IPage<OperationLog> pageLogs(int pageNum, int pageSize, Long userId, String operation, String targetType);
    void logOperation(Long userId, String module, String operation, String targetType, Long targetId, String targetName, String detail, String result, String ipAddress);
}
