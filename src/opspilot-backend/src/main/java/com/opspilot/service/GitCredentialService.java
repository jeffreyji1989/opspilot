package com.opspilot.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.opspilot.entity.GitCredential;

public interface GitCredentialService extends IService<GitCredential> {
    IPage<GitCredential> pageCredentials(int pageNum, int pageSize, String keyword);
    GitCredential createCredential(GitCredential credential, String plainText);
    String getDecryptedData(GitCredential credential);
}
