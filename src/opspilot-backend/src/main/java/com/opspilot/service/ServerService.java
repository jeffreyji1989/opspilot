package com.opspilot.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.opspilot.entity.Server;

import java.util.List;
import java.util.Map;

/**
 * 服务器服务接口
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
public interface ServerService extends IService<Server> {

    /**
     * 分页查询服务器
     */
    IPage<Server> pageServers(int pageNum, int pageSize, Integer envType, String keyword);

    /**
     * 按环境分组查询服务器
     *
     * @return key=envType(0-dev,1-test,2-staging,3-prod), value=server list
     */
    Map<Integer, List<Server>> listServersByEnv();

    /**
     * 添加服务器并建立 SSH 互信
     */
    void addServerWithSsh(Server server, String sshPassword);

    /**
     * 探测服务器环境信息
     */
    void detectServerEnv(Long serverId);

    /**
     * 更新所有服务器在线/离线状态
     */
    void updateAllServerStatus();

    /**
     * 删除服务器（有服务运行中时拒绝）
     */
    void deleteServer(Long id);

    /**
     * 获取 SSH 私钥路径
     */
    String getSshPrivateKeyPath();
}
