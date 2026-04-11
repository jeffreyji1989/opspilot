package com.opspilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.opspilot.entity.OperationLog;
import com.opspilot.mapper.OperationLogMapper;
import com.opspilot.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {

    @Override
    public IPage<OperationLog> pageLogs(int pageNum, int pageSize, Long userId, String operation, String targetType) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) wrapper.eq(OperationLog::getUserId, userId);
        if (StringUtils.hasText(operation)) wrapper.eq(OperationLog::getOperation, operation);
        if (StringUtils.hasText(targetType)) wrapper.eq(OperationLog::getTargetType, targetType);
        wrapper.orderByDesc(OperationLog::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public void logOperation(Long userId, String module, String operation, String targetType,
                             Long targetId, String targetName, String detail, String result, String ipAddress) {
        OperationLog log = new OperationLog();
        log.setUserId(userId);
        log.setModule(module);
        log.setOperation(operation);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setTargetName(targetName);
        log.setRequestParams(detail);
        log.setIpAddress(ipAddress);
        save(log);
    }
}
