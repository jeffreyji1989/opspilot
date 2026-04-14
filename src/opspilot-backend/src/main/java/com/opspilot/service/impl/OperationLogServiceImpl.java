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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 操作日志服务实现类
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {

    @Override
    public IPage<OperationLog> pageLogs(int pageNum, int pageSize, Long userId, String operation, String targetType) {
        return pageLogs(pageNum, pageSize, userId, operation, targetType, null, null, null, null);
    }

    @Override
    public IPage<OperationLog> pageLogs(int pageNum, int pageSize, Long userId, String operation,
                                        String targetType, String targetName, String result,
                                        String startDate, String endDate) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) wrapper.eq(OperationLog::getUserId, userId);
        if (StringUtils.hasText(operation)) wrapper.eq(OperationLog::getOperation, operation);
        if (StringUtils.hasText(targetType)) wrapper.eq(OperationLog::getTargetType, targetType);
        if (StringUtils.hasText(targetName)) wrapper.like(OperationLog::getTargetName, targetName);
        if (StringUtils.hasText(result)) wrapper.eq(OperationLog::getResult, result);
        if (StringUtils.hasText(startDate)) {
            LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
            wrapper.ge(OperationLog::getCreateTime, start);
        }
        if (StringUtils.hasText(endDate)) {
            LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
            wrapper.le(OperationLog::getCreateTime, end);
        }
        wrapper.orderByDesc(OperationLog::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public List<OperationLog> exportLogs(String operation, String targetType, String targetName,
                                         String result, String startDate, String endDate) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(operation)) wrapper.eq(OperationLog::getOperation, operation);
        if (StringUtils.hasText(targetType)) wrapper.eq(OperationLog::getTargetType, targetType);
        if (StringUtils.hasText(targetName)) wrapper.like(OperationLog::getTargetName, targetName);
        if (StringUtils.hasText(result)) wrapper.eq(OperationLog::getResult, result);
        if (StringUtils.hasText(startDate)) {
            LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
            wrapper.ge(OperationLog::getCreateTime, start);
        }
        if (StringUtils.hasText(endDate)) {
            LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
            wrapper.le(OperationLog::getCreateTime, end);
        }
        wrapper.orderByDesc(OperationLog::getCreateTime);
        return list(wrapper);
    }

    @Override
    public void logOperation(Long userId, String module, String operation, String targetType,
                             Long targetId, String targetName, String detail, String result, String ipAddress) {
        OperationLog logEntry = new OperationLog();
        logEntry.setUserId(userId);
        logEntry.setModule(module);
        logEntry.setOperation(operation);
        logEntry.setTargetType(targetType);
        logEntry.setTargetId(targetId);
        logEntry.setTargetName(targetName);
        logEntry.setRequestParams(detail);
        logEntry.setResult(result);
        logEntry.setIpAddress(ipAddress);
        save(logEntry);
    }
}
