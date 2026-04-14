package com.opspilot.controller;

import com.opspilot.common.PageResult;
import com.opspilot.common.Result;
import com.opspilot.entity.GitCredential;
import com.opspilot.service.GitCredentialService;
import com.opspilot.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Git 认证管理 Controller
 *
 * <p>提供 Git 认证的增删改查接口，敏感信息 AES-256-GCM 加密存储，
 * 列表页隐藏敏感字段，被模块关联时禁止删除。</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@RestController
@RequestMapping("/api/git-credentials")
@RequiredArgsConstructor
public class GitCredentialController {

    private final GitCredentialService gitCredentialService;
    private final OperationLogService operationLogService;

    /**
     * 分页查询认证列表（不返回敏感字段）
     */
    @GetMapping
    public Result<PageResult<GitCredential>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(PageResult.of(gitCredentialService.pageCredentials(pageNum, pageSize, keyword)));
    }

    /**
     * 获取认证详情（含解密后的敏感信息）
     */
    @GetMapping("/{id}")
    public Result<GitCredential> getById(@PathVariable Long id) {
        GitCredential credential = gitCredentialService.getByIdWithDecrypted(id);
        if (credential == null) {
            return Result.error(404, "认证不存在");
        }
        return Result.success(credential);
    }

    /**
     * 创建认证（加密存储敏感信息）
     */
    @PostMapping
    public Result<GitCredential> create(@RequestBody GitCredentialCreateRequest req, HttpServletRequest request) {
        GitCredential credential = new GitCredential();
        credential.setCredentialName(req.getCredentialName());
        credential.setCredentialType(req.getCredentialType());
        credential.setUsername(req.getUsername());
        credential.setDomain(req.getDomain());
        credential.setStatus(req.getStatus() != null ? req.getStatus() : 1);

        GitCredential saved = gitCredentialService.createCredential(credential, req.getPlainText());
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "gitCredential", "CREATE", "GIT_CREDENTIAL", saved.getId(),
                saved.getCredentialName(), "创建Git认证", "success", request.getRemoteAddr());
        return Result.success(saved);
    }

    /**
     * 编辑认证（支持更新加密数据）
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody GitCredentialUpdateRequest req, HttpServletRequest request) {
        gitCredentialService.updateCredential(id, req.getCredentialName(), req.getUsername(),
                req.getDomain(), req.getStatus(), req.getPlainText());
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "gitCredential", "UPDATE", "GIT_CREDENTIAL", id,
                req.getCredentialName(), "更新Git认证", "success", request.getRemoteAddr());
        return Result.success();
    }

    /**
     * 删除认证（被模块关联时拒绝）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        GitCredential credential = gitCredentialService.getById(id);
        if (credential == null) {
            return Result.error(404, "认证不存在");
        }
        gitCredentialService.deleteCredential(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "gitCredential", "DELETE", "GIT_CREDENTIAL", id,
                credential.getCredentialName(), "删除Git认证", "success", request.getRemoteAddr());
        return Result.success();
    }

    /**
     * 创建认证请求
     */
    @Data
    public static class GitCredentialCreateRequest {
        private String credentialName;
        private Integer credentialType;
        private String username;
        private String domain;
        private String plainText;
        private Integer status;
    }

    /**
     * 更新认证请求
     */
    @Data
    public static class GitCredentialUpdateRequest {
        private String credentialName;
        private String username;
        private String domain;
        private String plainText;
        private Integer status;
    }
}
