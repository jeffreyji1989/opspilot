package com.opspilot.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.opspilot.entity.DeployRecord;
import com.opspilot.entity.ServiceInstance;

import java.util.List;
import java.util.Map;

/**
 * 服务实例服务接口
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
public interface ServiceInstanceService extends IService<ServiceInstance> {
    IPage<ServiceInstance> pageInstances(int pageNum, int pageSize, Long moduleId, Long serverId, Integer status);
    void createInstanceWithDirSetup(ServiceInstance instance);
    String startService(Long instanceId);
    String stopService(Long instanceId);
    String restartService(Long instanceId);
    String getLogContent(Long instanceId, int lines);
    void rollback(Long instanceId, String targetVersion);
    String getProcessInfo(Long instanceId);
    List<DeployRecord> getVersions(Long instanceId);
    Map<String, Object> getConfig(Long instanceId);
    Map<String, Object> getMonitor(Long instanceId);
}
