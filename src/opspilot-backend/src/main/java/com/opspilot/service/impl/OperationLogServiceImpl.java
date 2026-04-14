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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 操作日志服务实现类
 *
 * <p>提供操作日志的持久化、分页查询、多条件筛选和 CSV 导出功能。</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public IPage<OperationLog> pageLogs(int pageNum, int pageSize, Long userId, String operation, String targetType) {
        return pageLogsWithFilter(pageNum, pageSize, userId, operation, null, targetType, null, null);
    }

    @Override
    public IPage<OperationLog> pageLogsWithFilter(int pageNum, int pageSize, Long userId, String operation,
                                                   String module, String targetType, String startDate, String endDate) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) wrapper.eq(OperationLog::getUserId, userId);
        if (StringUtils.hasText(operation)) wrapper.eq(OperationLog::getOperation, operation);
        if (StringUtils.hasText(module)) wrapper.eq(OperationLog::getModule, module);
        if (StringUtils.hasText(targetType)) wrapper.eq(OperationLog::getTargetType, targetType);
        if (StringUtils.hasText(startDate)) {
            try {
                LocalDateTime start = LocalDateTime.parse(startDate + " 00:00:00", DATE_FMT);
                wrapper.ge(OperationLog::getCreateTime, start);
            } catch (Exception ignored) {
            }
        }
        if (StringUtils.hasText(endDate)) {
            try {
                LocalDateTime end = LocalDateTime.parse(endDate + " 23:59:59", DATE_FMT);
                wrapper.le(OperationLog::getCreateTime, end);
            } catch (Exception ignored) {
            }
        }
        wrapper.orderByDesc(OperationLog::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public String exportCsv(Long userId, String operation, String module, String targetType, String startDate, String endDate) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) wrapper.eq(OperationLog::getUserId, userId);
        if (StringUtils.hasText(operation)) wrapper.eq(OperationLog::getOperation, operation);
        if (StringUtils.hasText(module)) wrapper.eq(OperationLog::getModule, module);
        if (StringUtils.hasText(targetType)) wrapper.eq(OperationLog::getTargetType, targetType);
        if (StringUtils.hasText(startDate)) {
            try {
                LocalDateTime start = LocalDateTime.parse(startDate + " 00:00:00", DATE_FMT);
                wrapper.ge(OperationLog::getCreateTime, start);
            } catch (Exception ignored) {
            }
        }
        if (StringUtils.hasText(endDate)) {
            try {
                LocalDateTime end = LocalDateTime.parse(endDate + " 23:59:59", DATE_FMT);
                wrapper.le(OperationLog::getCreateTime, end);
            } catch (Exception ignored) {
            }
        }
        wrapper.orderByDesc(OperationLog::getCreateTime);
        List<OperationLog> logs = list(wrapper);

        StringBuilder csv = new StringBuilder();
        csv.append("ID,操作人ID,操作模块,操作类型,目标类型,目标名称,详情,IP地址,操作时间\n");
        for (OperationLog log : logs) {
            csv.append(log.getId()).append(",");
            csv.append(log.getUserId()).append(",");
            csv.append(escapeCsv(log.getModule())).append(",");
            csv.append(escapeCsv(log.getOperation())).append(",");
            csv.append(escapeCsv(log.getTargetType())).append(",");
            csv.append(escapeCsv(log.getTargetName())).append(",");
            csv.append(escapeCsv(log.getRequestParams())).append(",");
            csv.append(escapeCsv(log.getIpAddress())).append(",");
            csv.append(log.getCreateTime() != null ? log.getCreateTime().format(DATE_FMT) : "");
            csv.append("\n");
        }
        return csv.toString();
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

    /**
     * CSV 字段转义（处理逗号、引号、换行）
     */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
