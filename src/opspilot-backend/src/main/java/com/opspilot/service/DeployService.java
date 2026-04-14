package com.opspilot.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.opspilot.common.Result;
import com.opspilot.dto.DeployRequest;
import com.opspilot.entity.DeployRecord;

import java.util.List;

/**
 * 发版部署服务接口
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
public interface DeployService extends IService<DeployRecord> {
    Long startDeploy(DeployRequest request, Long operatorId, String username);
    IPage<DeployRecord> pageDeployRecords(int pageNum, int pageSize, Long instanceId);
    void cancelDeploy(Long deployRecordId);
    String getDeployLog(Long deployRecordId);

    /**
     * 执行发版部署
     */
    Result<Long> deploy(Long moduleId, Long instanceId, String operator);

    /**
     * 执行版本回退（回退到上一个版本）
     */
    Result<Long> rollback(Long instanceId, String operator);

    /**
     * 回退到指定版本
     */
    Result<Long> rollbackToVersion(Long instanceId, String targetVersion, String operator);

    /**
     * 查询发版进度
     */
    Result<DeployRecord> getProgress(Long recordId);

    /**
     * 查询发版历史记录
     */
    Result<List<DeployRecord>> getHistory(Long moduleId);

    /**
     * 查询发版历史记录（带筛选）
     */
    Result<List<DeployRecord>> getHistory(Long moduleId, Integer status, String startDate, String endDate);

    /**
     * 分页查询发版历史（带筛选）
     */
    IPage<DeployRecord> pageDeployRecordsWithFilter(int pageNum, int pageSize, Long moduleId, Long instanceId, Integer status);
}
