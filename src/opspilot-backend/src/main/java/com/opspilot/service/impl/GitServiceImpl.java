package com.opspilot.service.impl;

import cn.hutool.core.util.StrUtil;
import com.opspilot.common.BusinessException;
import com.opspilot.common.SshManager;
import com.opspilot.entity.Module;
import com.opspilot.entity.Server;
import com.opspilot.service.GitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Git 服务实现类
 *
 * <p>通过 SSH 在目标服务器上执行 Git 命令获取分支列表。</p>
 *
 * @author opspilot-team
 * @since 2026-04-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitServiceImpl implements GitService {

    private final SshManager sshManager;

    @Override
    public List<String> listBranches(Module module) {
        if (module == null || StrUtil.isBlank(module.getRepoUrl())) {
            throw new BusinessException("模块或仓库地址不存在");
        }
        try {
            // 在本地执行 git ls-remote 获取分支列表
            String cmd = String.format("git ls-remote --heads %s 2>/dev/null", module.getRepoUrl());
            String output = sshManager.executeCommand(cmd, null, 30);
            return parseBranches(output);
        } catch (Exception e) {
            log.error("获取分支列表失败: {}", e.getMessage());
            throw new BusinessException("获取分支列表失败: " + e.getMessage());
        }
    }

    @Override
    public List<String> listBranchesFromServer(Module module, Server server) {
        if (module == null || StrUtil.isBlank(module.getRepoUrl())) {
            throw new BusinessException("模块或仓库地址不存在");
        }
        if (server == null) {
            throw new BusinessException("服务器不存在");
        }

        String tempDir = "/tmp/opspilot/git-list-" + module.getId();
        try {
            SSHClient ssh = sshManager.connect(server);
            try {
                // 确保临时目录存在
                sshManager.executeCommand(ssh, "mkdir -p " + tempDir, 10);

                // 通过 SSH 执行 git ls-remote
                String cmd = String.format("cd %s && git ls-remote --heads %s 2>/dev/null", tempDir, module.getRepoUrl());
                String output = sshManager.executeCommand(ssh, cmd, 30);
                return parseBranches(output);
            } finally {
                ssh.close();
            }
        } catch (Exception e) {
            log.error("获取分支列表失败: {}", e.getMessage());
            throw new BusinessException("获取分支列表失败: " + e.getMessage());
        } finally {
            // 清理临时目录
            try {
                SSHClient ssh = sshManager.connect(server);
                sshManager.executeCommand(ssh, "rm -rf " + tempDir, 5);
                ssh.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 解析 git ls-remote 输出，提取分支名称
     *
     * <p>输出格式示例：
     * abc123\trefs/heads/main
     * def456\trefs/heads/develop</p>
     *
     * @param output git ls-remote 输出
     * @return 分支名称列表
     */
    private List<String> parseBranches(String output) {
        if (StrUtil.isBlank(output)) {
            return Collections.emptyList();
        }
        return Arrays.stream(output.split("\n"))
                .map(String::trim)
                .filter(line -> line.contains("refs/heads/"))
                .map(line -> {
                    int idx = line.indexOf("refs/heads/");
                    return line.substring(idx + "refs/heads/".length());
                })
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }
}
