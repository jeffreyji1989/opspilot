package com.opspilot.service;

import cn.hutool.http.HttpUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉通知服务
 *
 * <p>提供钉钉 Webhook 通知发送功能，支持签名验证。</p>
 *
 * @author opspilot-team
 * @since 2026-04-14
 */
@Slf4j
@Service
public class DingTalkService {

    /**
     * 发送钉钉文本消息
     *
     * @param webhook  Webhook 地址（含签名参数或完整 URL）
     * @param secret   签名密钥（可为空）
     * @param title    消息标题
     * @param content  消息内容
     * @param atUserIds 需要 @ 的用户 ID 列表（可选）
     * @return 是否发送成功
     */
    public boolean sendTextMessage(String webhook, String secret, String title, String content, String atUserIds) {
        try {
            String url = webhook;
            // 如果提供了 secret，需要计算签名
            if (secret != null && !secret.isEmpty() && !webhook.contains("sign=")) {
                long timestamp = System.currentTimeMillis();
                String stringToSign = timestamp + "\n" + secret;
                HMac mac = new HMac(HmacAlgorithm.HmacSHA256, secret.getBytes(StandardCharsets.UTF_8));
                String sign = URLEncoder.encode(mac.digestBase64(stringToSign, StandardCharsets.UTF_8, false), StandardCharsets.UTF_8);
                url = webhook + "&timestamp=" + timestamp + "&sign=" + sign;
            }

            // 构建消息体
            Map<String, Object> message = new HashMap<>();
            message.put("msgtype", "text");

            Map<String, String> text = new HashMap<>();
            text.put("content", (title != null ? "【" + title + "】\n" : "") + content);
            message.put("text", text);

            // @ 用户配置
            if (atUserIds != null && !atUserIds.isEmpty()) {
                Map<String, Object> at = new HashMap<>();
                String[] userIds = atUserIds.split(",");
                at.put("atUserIds", userIds);
                message.put("at", at);
            }

            String requestBody = JSONUtil.toJsonStr(message);
            String response = HttpUtil.post(url, requestBody);

            JSONObject result = JSONUtil.parseObj(response);
            if (result.getInt("errcode") == 0) {
                log.info("钉钉消息发送成功: title={}", title);
                return true;
            } else {
                log.error("钉钉消息发送失败: errcode={}, errmsg={}", result.getInt("errcode"), result.getStr("errmsg"));
                return false;
            }
        } catch (Exception e) {
            log.error("钉钉消息发送异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送钉钉 Markdown 消息
     *
     * @param webhook  Webhook 地址
     * @param secret   签名密钥
     * @param title    消息标题
     * @param text     Markdown 内容
     * @return 是否发送成功
     */
    public boolean sendMarkdownMessage(String webhook, String secret, String title, String text) {
        try {
            String url = webhook;
            if (secret != null && !secret.isEmpty() && !webhook.contains("sign=")) {
                long timestamp = System.currentTimeMillis();
                String stringToSign = timestamp + "\n" + secret;
                HMac mac = new HMac(HmacAlgorithm.HmacSHA256, secret.getBytes(StandardCharsets.UTF_8));
                String sign = URLEncoder.encode(mac.digestBase64(stringToSign, StandardCharsets.UTF_8, false), StandardCharsets.UTF_8);
                url = webhook + "&timestamp=" + timestamp + "&sign=" + sign;
            }

            Map<String, Object> message = new HashMap<>();
            message.put("msgtype", "markdown");

            Map<String, String> markdown = new HashMap<>();
            markdown.put("title", title);
            markdown.put("text", text);
            message.put("markdown", markdown);

            String requestBody = JSONUtil.toJsonStr(message);
            String response = HttpUtil.post(url, requestBody);

            JSONObject result = JSONUtil.parseObj(response);
            if (result.getInt("errcode") == 0) {
                log.info("钉钉 Markdown 消息发送成功: title={}", title);
                return true;
            } else {
                log.error("钉钉 Markdown 消息发送失败: errcode={}, errmsg={}", result.getInt("errcode"), result.getStr("errmsg"));
                return false;
            }
        } catch (Exception e) {
            log.error("钉钉 Markdown 消息发送异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送部署结果通知
     *
     * @param webhook    Webhook 地址
     * @param secret     签名密钥
     * @param serviceName 服务名称
     * @param version    版本号
     * @param status     部署状态（success/failed）
     * @param duration   耗时（秒）
     * @param errorMsg   错误信息（可选）
     * @return 是否发送成功
     */
    public boolean sendDeployNotification(String webhook, String secret, String serviceName,
                                           String version, String status, String duration, String errorMsg) {
        String emoji = "success".equals(status) ? "✅" : "❌";
        String statusText = "success".equals(status) ? "部署成功" : "部署失败";

        String markdown = String.format(
                "## %s 发版通知\n\n" +
                "- **服务**: %s\n" +
                "- **版本**: %s\n" +
                "- **状态**: %s\n" +
                "- **耗时**: %s 秒\n" +
                "%s",
                emoji, serviceName, version, statusText, duration,
                errorMsg != null ? "- **错误**: " + errorMsg + "\n" : ""
        );

        return sendMarkdownMessage(webhook, secret, "OpsPilot 发版通知", markdown);
    }
}
