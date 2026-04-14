package com.opspilot.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.opspilot.entity.ServiceInstance;

import java.util.List;

/**
 * 服务实例服务接口
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
public interface ServiceInstanceService extends IService<ServiceInstance> {

    /**
     * 分页查询服务实例
     */
    IPage<ServiceInstance> pageInstances(int pageNum, int pageSize, Long moduleId, Long serverId, Integer processStatus);

    /**
     * 创建服务实例（含目录初始化）
     */
    void createInstanceWithDirSetup(ServiceInstance instance);

    /**
     * 启动服务
     */
    String startService(Long instanceId);

    /**
     * 停止服务
     */
    String stopService(Long instanceId);

    /**
     * 重启服务
     */
    String restartService(Long instanceId);

    /**
     * 获取历史日志
     */
    String getLogContent(Long instanceId, int lines);

    /**
     * 版本回退到指定版本
     */
    void rollback(Long instanceId, String targetVersion);

    /**
     * 获取进程信息
     */
    String getProcessInfo(Long instanceId);

    /**
     * 获取监控数据（CPU/内存/磁盘）
     */
    String getMonitorInfo(Long instanceId);

    /**
     * 获取历史版本列表
     */
    List<String> getVersions(Long instanceId);
}
