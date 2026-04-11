package com.opspilot.controller;

import com.opspilot.common.PageResult;
import com.opspilot.common.Result;
import com.opspilot.entity.Server;
import com.opspilot.service.OperationLogService;
import com.opspilot.service.ServerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
public class ServerController {

    private final ServerService serverService;
    private final OperationLogService operationLogService;

    @GetMapping
    public Result<PageResult<Server>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer envType,
            @RequestParam(required = false) String keyword) {
        return Result.success(PageResult.of(serverService.pageServers(pageNum, pageSize, envType, keyword)));
    }

    @GetMapping("/{id}")
    public Result<Server> getById(@PathVariable Long id) {
        return Result.success(serverService.getById(id));
    }

    @PostMapping
    public Result<Server> create(@Valid @RequestBody ServerCreateRequest req, HttpServletRequest request) {
        Server server = req.getServer();
        serverService.addServerWithSsh(server, req.getSshPassword());
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "server", "CREATE", "SERVER", server.getId(),
                server.getServerName(), "添加服务器 (SSH互信)", "success", request.getRemoteAddr());
        return Result.success(server);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Server server, HttpServletRequest request) {
        server.setId(id);
        serverService.updateById(server);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "server", "UPDATE", "SERVER", id,
                server.getServerName(), "更新服务器", "success", request.getRemoteAddr());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Server server = serverService.getById(id);
        serverService.removeById(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "server", "DELETE", "SERVER", id,
                server != null ? server.getServerName() : "", "删除服务器", "success", request.getRemoteAddr());
        return Result.success();
    }

    @PostMapping("/{id}/detect")
    public Result<Void> detectEnv(@PathVariable Long id, HttpServletRequest request) {
        serverService.detectServerEnv(id);
        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "server", "DETECT", "SERVER", id,
                "", "环境探测", "success", request.getRemoteAddr());
        return Result.success();
    }

    @Data
    public static class ServerCreateRequest {
        @NotNull(message = "服务器信息不能为空")
        private Server server;
        private String sshPassword;
    }
}
