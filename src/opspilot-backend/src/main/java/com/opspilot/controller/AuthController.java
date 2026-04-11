package com.opspilot.controller;

import com.opspilot.common.Result;
import com.opspilot.dto.LoginRequest;
import com.opspilot.dto.LoginResponse;
import com.opspilot.service.AuthService;
import com.opspilot.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OperationLogService operationLogService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        LoginResponse response = authService.login(request, ip);
        operationLogService.logOperation(response.getUserId(), "auth", "LOGIN", "USER",
                response.getUserId(), response.getUsername(), "用户登录", "success", ip);
        return Result.success(response);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isEmpty()) ip = request.getRemoteAddr();
        return ip;
    }
}
