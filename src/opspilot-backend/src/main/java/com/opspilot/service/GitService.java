package com.opspilot.service;

import com.opspilot.entity.Module;
import com.opspilot.entity.Server;

import java.util.List;

/**
 * Git 服务接口
 *
 * <p>提供 Git 仓库操作，包括拉取分支列表等。</p>
 *
 * @author opspilot-team
 * @since 2026-04-14
 */
public interface GitService {

    /**
     * 拉取 Git 仓库的分支列表
     *
     * @param module 模块信息（包含仓库地址和认证信息）
     * @return 分支名称列表
     */
    List<String> listBranches(Module module);

    /**
     * 通过服务器拉取 Git 仓库的分支列表
     *
     * @param module 模块信息
     * @param server 目标服务器（用于执行 git 命令）
     * @return 分支名称列表
     */
    List<String> listBranchesFromServer(Module module, Server server);
}
