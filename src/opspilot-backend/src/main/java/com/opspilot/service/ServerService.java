package com.opspilot.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.opspilot.entity.Server;

public interface ServerService extends IService<Server> {
    IPage<Server> pageServers(int pageNum, int pageSize, Integer envType, String keyword);
    void addServerWithSsh(Server server, String sshPassword);
    void detectServerEnv(Long serverId);
    String getSshPrivateKeyPath();
}
