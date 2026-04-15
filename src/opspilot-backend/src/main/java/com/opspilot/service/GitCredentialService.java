package com.opspilot.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.opspilot.entity.GitCredential;

/**
 * Git 认证服务接口
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
public interface GitCredentialService extends IService<GitCredential> {

    /**
     * 分页查询认证列表（不返回敏感字段）
     */
    IPage<GitCredential> pageCredentials(int pageNum, int pageSize, String keyword);

    /**
     * 获取认证详情（含解密后的敏感信息）
     */
    GitCredential getByIdWithDecrypted(Long id);

    /**
     * 创建认证（加密存储敏感信息）
     */
    GitCredential createCredential(GitCredential credential, String plainText);

    /**
     * 更新认证（如果 plainText 不为空则重新加密）
     */
    void updateCredential(Long id, String credentialName, String username, String domain, Integer status, String plainText);

    /**
     * 删除认证（被模块关联时拒绝）
     */
    void deleteCredential(Long id);

    /**
     * 解密敏感数据
     */
    String getDecryptedData(GitCredential credential);
}
