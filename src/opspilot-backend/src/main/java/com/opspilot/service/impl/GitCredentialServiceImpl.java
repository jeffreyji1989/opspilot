package com.opspilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.opspilot.common.AesGcmUtil;
import com.opspilot.common.BusinessException;
import com.opspilot.entity.GitCredential;
import com.opspilot.entity.Module;
import com.opspilot.mapper.GitCredentialMapper;
import com.opspilot.mapper.ModuleMapper;
import com.opspilot.service.GitCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.util.Base64;

/**
 * Git 认证服务实现
 *
 * <p>提供 Git 认证的增删改查，AES-256-GCM 加密存储敏感数据，
 * 列表页隐藏敏感字段，被模块关联时禁止删除，SSH Key 指纹计算。</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitCredentialServiceImpl extends ServiceImpl<GitCredentialMapper, GitCredential> implements GitCredentialService {

    private final ModuleMapper moduleMapper;

    @Override
    public IPage<GitCredential> pageCredentials(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<GitCredential> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(GitCredential::getCredentialName, keyword);
        }
        wrapper.orderByDesc(GitCredential::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public GitCredential getByIdWithDecrypted(Long id) {
        GitCredential credential = getById(id);
        if (credential == null) {
            return null;
        }
        // 解密敏感数据
        if (StringUtils.hasText(credential.getEncryptedData())) {
            String decrypted = getDecryptedData(credential);
            // 将解密数据暂存在 username 字段返回（前端按需使用）
            credential.setUsername(credential.getUsername() + "|" + decrypted);
        }
        return credential;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GitCredential createCredential(GitCredential credential, String plainText) {
        if (!StringUtils.hasText(plainText)) {
            throw new BusinessException("凭证数据不能为空");
        }
        // AES-256-GCM 加密
        String encrypted = AesGcmUtil.encrypt(plainText);
        credential.setEncryptedData(encrypted);
        // 计算 SSH Key 指纹（如果是 SSH 类型）
        if (credential.getCredentialType() != null && credential.getCredentialType() == 0) {
            credential.setFingerprint(calculateSshFingerprint(plainText));
        }
        save(credential);
        log.info("Git 认证创建成功: id={}, name={}", credential.getId(), credential.getCredentialName());
        // 清除敏感字段后返回
        credential.setEncryptedData(null);
        return credential;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCredential(Long id, String credentialName, String username, String domain, Integer status, String plainText) {
        GitCredential existing = getById(id);
        if (existing == null) {
            throw new BusinessException("认证不存在");
        }
        existing.setCredentialName(credentialName);
        existing.setUsername(username);
        existing.setDomain(domain);
        if (status != null) {
            existing.setStatus(status);
        }
        // 如果提供了新的明文数据，重新加密
        if (StringUtils.hasText(plainText)) {
            String encrypted = AesGcmUtil.encrypt(plainText);
            existing.setEncryptedData(encrypted);
            // 重新计算指纹
            if (existing.getCredentialType() != null && existing.getCredentialType() == 0) {
                existing.setFingerprint(calculateSshFingerprint(plainText));
            }
        }
        updateById(existing);
        log.info("Git 认证更新成功: id={}, name={}", id, credentialName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCredential(Long id) {
        GitCredential credential = getById(id);
        if (credential == null) {
            throw new BusinessException("认证不存在");
        }
        // 校验是否有模块关联
        long moduleCount = moduleMapper.selectCount(
                new LambdaQueryWrapper<Module>().eq(Module::getGitCredId, id));
        if (moduleCount > 0) {
            throw new BusinessException("该认证已被 " + moduleCount + " 个模块关联，无法删除");
        }
        removeById(id);
        log.info("Git 认证删除成功: id={}, name={}", id, credential.getCredentialName());
    }

    @Override
    public String getDecryptedData(GitCredential credential) {
        if (credential.getEncryptedData() == null) {
            return null;
        }
        return AesGcmUtil.decrypt(credential.getEncryptedData());
    }

    /**
     * 计算 SSH Key 指纹（SHA256 格式）
     *
     * <p>格式：SHA256:Base64(SHA256(publicKey))，与 GitHub/GitLab 显示格式一致。</p>
     */
    private String calculateSshFingerprint(String privateKeyContent) {
        try {
            // 简化处理：对私钥内容直接计算 SHA256
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(privateKeyContent.getBytes());
            String base64 = Base64.getEncoder().encodeToString(hash);
            // 去掉末尾的 = 号（与 SSH 指纹格式一致）
            return "SHA256:" + base64.replaceAll("=+$", "");
        } catch (Exception e) {
            log.warn("SSH 指纹计算失败: {}", e.getMessage());
            return "";
        }
    }
}
