package com.opspilot.controller;

import com.opspilot.common.Result;
import com.opspilot.service.DingTalkService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统设置控制器
 *
 * <p>提供系统配置参数的查询和更新功能。</p>
 *
 * @author opspilot-team
 * @since 2026-04-14
 */
@RestController
@RequestMapping("/api/system-settings")
@RequiredArgsConstructor
public class SystemSettingsController {

    private final DingTalkService dingTalkService;

    @Value("${opspilot.dingtalk.webhook:}")
    private String defaultWebhook;

    @Value("${opspilot.dingtalk.secret:}")
    private String defaultSecret;

    @Value("${opspilot.deploy.build-timeout:300}")
    private int defaultBuildTimeout;

    @Value("${opspilot.deploy.health-check-path:/actuator/health}")
    private String defaultHealthCheckPath;

    @Value("${opspilot.deploy.health-check-timeout:60}")
    private int defaultHealthCheckTimeout;

    /**
     * 获取系统设置
     *
     * @return 系统设置
     */
    @GetMapping
    public Result<Map<String, Object>> getSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("dingtalkWebhook", maskWebhook(defaultWebhook));
        settings.put("dingtalkEnabled", defaultWebhook != null && !defaultWebhook.isEmpty());
        settings.put("buildTimeout", defaultBuildTimeout);
        settings.put("healthCheckPath", defaultHealthCheckPath);
        settings.put("healthCheckTimeout", defaultHealthCheckTimeout);
        return Result.success(settings);
    }

    /**
     * 更新系统设置
     *
     * @param request 设置请求
     * @return 操作结果
     */
    @PostMapping
    public Result<Void> updateSettings(@RequestBody SettingsRequest request) {
        // 在实际生产中，设置应该持久化到数据库或配置中心
        // 这里仅作演示，实际应用中应使用 @RefreshScope 或配置中心
        return Result.success();
    }

    /**
     * 测试钉钉通知
     *
     * @param webhook Webhook URL
     * @param secret  加签密钥
     * @return 测试结果
     */
    @PostMapping("/test-dingtalk")
    public Result<Boolean> testDingTalk(@RequestBody Map<String, String> request) {
        String webhook = request.getOrDefault("webhook", defaultWebhook);
        String secret = request.getOrDefault("secret", defaultSecret);

        if (webhook == null || webhook.isEmpty()) {
            return Result.error("钉钉 Webhook 未配置");
        }

        boolean success = dingTalkService.sendMarkdown(
                webhook, secret,
                "OpsPilot 测试通知",
                "## 🧪 OpsPilot 测试通知\n\n这是一条测试消息，用于验证钉钉通知配置是否正确。\n\n> 由 OpsPilot 系统设置页面发送",
                null);

        return success ? Result.success(true) : Result.error(500, "发送失败，请检查 Webhook 配置", false);
    }

    /**
     * 脱敏 Webhook URL
     */
    private String maskWebhook(String webhook) {
        if (webhook == null || webhook.isEmpty()) return "";
        if (webhook.length() <= 20) return webhook.substring(0, 5) + "***" + webhook.substring(webhook.length() - 4);
        return webhook.substring(0, 10) + "***" + webhook.substring(webhook.length() - 6);
    }

    @Data
    public static class SettingsRequest {
        /** 钉钉 Webhook URL */
        private String dingtalkWebhook;
        /** 钉钉加签密钥 */
        private String dingtalkSecret;
        /** 默认构建超时（秒） */
        private Integer buildTimeout;
        /** 默认健康检查路径 */
        private String healthCheckPath;
        /** 默认健康检查超时（秒） */
        private Integer healthCheckTimeout;
    }
}
