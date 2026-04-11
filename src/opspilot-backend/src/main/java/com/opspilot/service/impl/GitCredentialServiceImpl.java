package com.opspilot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.opspilot.common.AesGcmUtil;
import com.opspilot.entity.GitCredential;
import com.opspilot.mapper.GitCredentialMapper;
import com.opspilot.service.GitCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class GitCredentialServiceImpl extends ServiceImpl<GitCredentialMapper, GitCredential> implements GitCredentialService {

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
    public GitCredential createCredential(GitCredential credential, String plainText) {
        String encrypted = AesGcmUtil.encrypt(plainText);
        credential.setEncryptedData(encrypted);
        save(credential);
        return credential;
    }

    @Override
    public String getDecryptedData(GitCredential credential) {
        if (credential.getEncryptedData() == null) {
            return null;
        }
        return AesGcmUtil.decrypt(credential.getEncryptedData());
    }
}
