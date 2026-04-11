package com.opspilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.opspilot.common.BusinessException;
import com.opspilot.common.SshManager;
import com.opspilot.entity.Server;
import com.opspilot.mapper.ServerMapper;
import com.opspilot.service.ServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerServiceImpl extends ServiceImpl<ServerMapper, Server> implements ServerService {

    private final SshManager sshManager;

    @Value("${opspilot.ssh.private-key-path}")
    private String privateKeyPath;

    @Override
    public IPage<Server> pageServers(int pageNum, int pageSize, Integer envType, String keyword) {
        LambdaQueryWrapper<Server> wrapper = new LambdaQueryWrapper<>();
        if (envType != null) {
            wrapper.eq(Server::getEnvType, envType);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Server::getServerName, keyword)
                    .or()
                    .like(Server::getHostname, keyword);
        }
        wrapper.orderByDesc(Server::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public void addServerWithSsh(Server server, String sshPassword) {
        // Check uniqueness
        if (getByServerName(server.getServerName()) != null) {
            throw new BusinessException("服务器名称已存在");
        }
        // Generate SSH key pair if not exists
        try {
            java.io.File keyFile = new java.io.File(privateKeyPath);
            if (!keyFile.exists()) {
                SshManager.generateKeyPair(privateKeyPath);
            }
            // Setup SSH trust
            String pubKey = java.nio.file.Files.readString(java.nio.file.Paths.get(privateKeyPath + ".pub"));
            sshManager.setupSshTrust(server, sshPassword, pubKey.trim());
            server.setSshKeyStatus(1);
        } catch (IOException e) {
            server.setSshKeyStatus(2);
            log.error("SSH trust setup failed for server {}: {}", server.getServerName(), e.getMessage());
        }
        // Detect environment
        try {
            detectServerEnvInternal(server);
        } catch (Exception e) {
            log.warn("Environment detection failed for {}: {}", server.getServerName(), e.getMessage());
        }
        server.setStatus(1);
        save(server);
    }

    @Override
    public void detectServerEnv(Long serverId) {
        Server server = getById(serverId);
        if (server == null) {
            throw new BusinessException("服务器不存在");
        }
        detectServerEnvInternal(server);
        server.setLastDetectTime(LocalDateTime.now());
        updateById(server);
    }

    private void detectServerEnvInternal(Server server) {
        try {
            SSHClient ssh = sshManager.connect(server);
            try {
                // OS info
                String osInfo = sshManager.executeCommand(ssh, "cat /etc/os-release 2>/dev/null | grep PRETTY_NAME | cut -d= -f2 | tr -d '\"'", 10);
                if (StringUtils.hasText(osInfo)) {
                    server.setOsType(osInfo.trim());
                }
                // JDK versions
                String jdk = sshManager.executeCommand(ssh, "java -version 2>&1 | head -1 | grep -oP '\\d+\\.\\d+\\.\\d+' || echo 'not found'", 10);
                if (StringUtils.hasText(jdk) && !jdk.contains("not found")) {
                    server.setOsVersion(jdk.trim());
                }
                // CPU
                String cpu = sshManager.executeCommand(ssh, "nproc", 5);
                if (StringUtils.hasText(cpu)) {
                    server.setCpuCores(Integer.parseInt(cpu.trim()));
                }
                // Memory
                String mem = sshManager.executeCommand(ssh, "free -m | awk '/^Mem:/{print $2}'", 5);
                if (StringUtils.hasText(mem)) {
                    server.setMemoryMb(Integer.parseInt(mem.trim()));
                }
                // Disk
                String disk = sshManager.executeCommand(ssh, "df -BG / | awk 'NR==2{print $2}' | tr -d 'G'", 5);
                if (StringUtils.hasText(disk)) {
                    server.setDiskTotalGb(Integer.parseInt(disk.trim()));
                }
            } finally {
                ssh.close();
            }
        } catch (Exception e) {
            log.error("Server environment detection failed: {}", e.getMessage());
            throw new BusinessException("环境探测失败: " + e.getMessage());
        }
    }

    @Override
    public String getSshPrivateKeyPath() {
        return privateKeyPath;
    }

    private Server getByServerName(String name) {
        return getOne(new LambdaQueryWrapper<Server>().eq(Server::getServerName, name), false);
    }
}
