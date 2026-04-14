package com.opspilot.controller;

import com.opspilot.common.Result;
import com.opspilot.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统设置控制器
 *
 * <p>提供系统级别的配置管理 API，包括钉钉通知配置、默认部署参数等。
 * 配置数据存储在内存中（ConcurrentHashMap），生产环境建议持久化到数据库。</p>
 *
 * @author opspilot-team
 * @since 2026-04-14
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SystemSettingsController {

    private final OperationLogService operationLogService;

    /** 系统配置存储（生产环境建议持久化到数据库） */
    private static final Map<String, Object> SETTINGS = new ConcurrentHashMap<>();

    static {
        // 默认配置
        SETTINGS.put("appName", "OpsPilot");
        SETTINGS.put("defaultGitBranch", "main");
        SETTINGS.put("defaultHealthCheckPath", "/actuator/health");
        SETTINGS.put("defaultHealthCheckTimeout", 30);
        SETTINGS.put("defaultHealthCheckRetries", 3);
        SETTINGS.put("dingtalkEnabled", false);
        SETTINGS.put("dingtalkWebhook", "");
        SETTINGS.put("dingtalkAtUserIds", "");
        SETTINGS.put("deployNotifyOnSuccess", true);
        SETTINGS.put("deployNotifyOnFailure", true);
    }

    /**
     * 查询所有系统设置
     *
     * @return 系统配置
     */
    @GetMapping
    public Result<Map<String, Object>> getAllSettings() {
        return Result.success(new HashMap<>(SETTINGS));
    }

    /**
     * 查询单个设置项
     *
     * @param key 配置键
     * @return 配置值
     */
    @GetMapping("/{key}")
    public Result<Object> getSetting(@PathVariable String key) {
        Object value = SETTINGS.get(key);
        if (value == null) {
            return Result.error("配置项不存在: " + key);
        }
        return Result.success(value);
    }

    /**
     * 更新系统设置
     *
     * @param request HTTP 请求
     * @param updates 更新请求
     * @return 操作结果
     */
    @PutMapping
    public Result<Void> updateSettings(@RequestBody SettingsUpdateRequest updates, HttpServletRequest request) {
        if (updates.getSettings() != null) {
            SETTINGS.putAll(updates.getSettings());
        }

        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "system", "UPDATE", "SETTINGS",
                null, "系统设置", "更新系统配置: " + updates.getSettings().keySet(), "success", request.getRemoteAddr());
        return Result.success();
    }

    /**
     * 重置系统设置为默认值
     *
     * @param request HTTP 请求
     * @return 操作结果
     */
    @PostMapping("/reset")
    public Result<Void> resetSettings(HttpServletRequest request) {
        SETTINGS.clear();
        SETTINGS.put("appName", "OpsPilot");
        SETTINGS.put("defaultGitBranch", "main");
        SETTINGS.put("defaultHealthCheckPath", "/actuator/health");
        SETTINGS.put("defaultHealthCheckTimeout", 30);
        SETTINGS.put("defaultHealthCheckRetries", 3);
        SETTINGS.put("dingtalkEnabled", false);
        SETTINGS.put("dingtalkWebhook", "");
        SETTINGS.put("dingtalkAtUserIds", "");
        SETTINGS.put("deployNotifyOnSuccess", true);
        SETTINGS.put("deployNotifyOnFailure", true);

        Long userId = (Long) request.getAttribute("userId");
        operationLogService.logOperation(userId, "system", "RESET", "SETTINGS",
                null, "系统设置", "重置系统配置", "success", request.getRemoteAddr());
        return Result.success();
    }

    /**
     * 设置更新请求体
     */
    @Data
    public static class SettingsUpdateRequest {
        private Map<String, Object> settings;
    }
}
