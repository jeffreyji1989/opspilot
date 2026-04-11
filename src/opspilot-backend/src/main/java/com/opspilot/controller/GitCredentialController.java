package com.opspilot.controller;

import com.opspilot.common.PageResult;
import com.opspilot.common.Result;
import com.opspilot.entity.GitCredential;
import com.opspilot.service.GitCredentialService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/git-credentials")
@RequiredArgsConstructor
public class GitCredentialController {

    private final GitCredentialService gitCredentialService;

    @GetMapping
    public Result<PageResult<GitCredential>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(PageResult.of(gitCredentialService.pageCredentials(pageNum, pageSize, keyword)));
    }

    @GetMapping("/{id}")
    public Result<GitCredential> getById(@PathVariable Long id) {
        return Result.success(gitCredentialService.getById(id));
    }

    @PostMapping
    public Result<GitCredential> create(@RequestBody GitCredentialCreateRequest req) {
        GitCredential credential = new GitCredential();
        credential.setCredentialName(req.getCredentialName());
        credential.setCredentialType(req.getCredentialType());
        credential.setUsername(req.getUsername());
        credential.setCredentialType(req.getCredentialType());
        if (req.getStatus() != null) credential.setStatus(req.getStatus());
        else credential.setStatus(1);
        GitCredential saved = gitCredentialService.createCredential(credential, req.getPlainText());
        return Result.success(saved);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody GitCredential credential) {
        credential.setId(id);
        gitCredentialService.updateById(credential);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        gitCredentialService.removeById(id);
        return Result.success();
    }

    @Data
    public static class GitCredentialCreateRequest {
        private String credentialName;
        private Integer credentialType;
        private String username;
        private String plainText;
        private Integer status;
    }
}
