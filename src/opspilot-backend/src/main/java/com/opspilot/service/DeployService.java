package com.opspilot.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.opspilot.dto.DeployRequest;
import com.opspilot.entity.DeployRecord;

public interface DeployService extends IService<DeployRecord> {
    Long startDeploy(DeployRequest request, Long operatorId, String username);
    IPage<DeployRecord> pageDeployRecords(int pageNum, int pageSize, Long instanceId);
    void cancelDeploy(Long deployRecordId);
    String getDeployLog(Long deployRecordId);
}
