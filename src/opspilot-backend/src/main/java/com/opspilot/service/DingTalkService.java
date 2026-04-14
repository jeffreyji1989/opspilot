package com.opspilot.service;

import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 钉钉通知服务
 *
 * <p>通过钉钉机器人 Webhook 发送通知消息。支持加签验证。</p>
 *
 * @author opspilot-team
 * @since 2026-04-14
 */
@Slf4j
@Service
public class DingTalkService {

    /**
     * 发送钉钉 Markdown 消息
     *
     * @param webhook   钉钉机器人 Webhook URL
     * @param secret    加签密钥（可为空）
     * @param title     消息标题
     * @param content   消息内容（支持 Markdown）
     * @param atUserIds @的用户ID列表（逗号分隔）
     * @return 是否发送成功
     */
    public boolean sendMarkdown(String webhook, String secret, String title, String content, String atUserIds) {
        try {
            String url = webhook;
            // 加签验证
            if (secret != null && !secret.isEmpty()) {
                long timestamp = System.currentTimeMillis();
                String stringToSign = timestamp + "\n" + secret;
                HMac mac = new HMac(HmacAlgorithm.HmacSHA256, secret.getBytes(StandardCharsets.UTF_8));
                byte[] signData = mac.digest(stringToSign.getBytes(StandardCharsets.UTF_8));
                String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
                url += (url.contains("?") ? "&" : "?") + "timestamp=" + timestamp + "&sign=" + sign;
            }

            JSONObject json = new JSONObject();
            json.set("msgtype", "markdown");
            JSONObject markdown = new JSONObject();
            markdown.set("title", title);
            markdown.set("text", content);
            json.set("markdown", markdown);

            // @用户
            if (atUserIds != null && !atUserIds.isEmpty()) {
                JSONObject at = new JSONObject();
                String[] userIds = atUserIds.split(",");
                at.set("atUserIds", userIds);
                json.set("at", at);
            }

            String response = HttpUtil.post(url, json.toString());
            JSONObject result = JSONUtil.parseObj(response);
            int errcode = result.getInt("errcode", -1);
            if (errcode == 0) {
                log.info("钉钉通知发送成功, title={}", title);
                return true;
            } else {
                log.warn("钉钉通知发送失败, errcode={}, errmsg={}", errcode, result.getStr("errmsg"));
                return false;
            }
        } catch (Exception e) {
            log.error("钉钉通知发送异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送部署结果通知
     *
     * @param webhook     Webhook URL
     * @param secret      加签密钥
     * @param serviceName 服务名称
     * @param version     版本号
     * @param status      部署状态
     * @param duration    耗时
     * @param errorMsg    错误信息
     * @param atUserIds   @的用户ID
     * @return 是否成功
     */
    public boolean sendDeployNotification(String webhook, String secret, String serviceName,
                                           String version, String status, String duration,
                                           String errorMsg, String atUserIds) {
        String statusEmoji = "成功".equals(status) ? "✅" : "❌";
        String title = statusEmoji + " OpsPilot 部署通知";

        StringBuilder content = new StringBuilder();
        content.append("## ").append(title).append("\n\n");
        content.append("- **服务名称**: ").append(serviceName).append("\n");
        content.append("- **版本号**: ").append(version).append("\n");
        content.append("- **部署状态**: ").append(statusEmoji).append(" ").append(status).append("\n");
        if (duration != null && !duration.isEmpty()) {
            content.append("- **耗时**: ").append(duration).append("\n");
        }
        if (errorMsg != null && !errorMsg.isEmpty()) {
            content.append("- **错误信息**: ").append(errorMsg).append("\n");
        }
        content.append("\n> 由 OpsPilot 自动发送");

        return sendMarkdown(webhook, secret, title, content.toString(), atUserIds);
    }
}
