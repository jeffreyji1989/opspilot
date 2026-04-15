package com.opspilot.websocket;

import com.opspilot.common.SshManager;
import com.opspilot.entity.Server;
import com.opspilot.entity.ServiceInstance;
import com.opspilot.mapper.ServerMapper;
import com.opspilot.mapper.ServiceInstanceMapper;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时日志 WebSocket 处理器
 *
 * <p>提供基于 WebSocket 的实时日志流推送功能。
 * 支持日志级别过滤、行数限制，连接关闭时自动终止 tail 进程。</p>
 *
 * <p>使用方式：ws://host/api/ws/log/{instanceId}?level=INFO&maxLines=1000&keyword=error</p>
 *
 * @author opspilot-team
 * @since 2026-04-14
 */
@Slf4j
@Component
@ServerEndpoint("/api/ws/log/{instanceId}")
public class LogWebSocket {

    private static SshManager sshManager;
    private static ServiceInstanceMapper instanceMapper;
    private static ServerMapper serverMapper;

    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private static final Map<String, SSHClient> sshClients = new ConcurrentHashMap<>();
    private static final Map<String, net.schmizz.sshj.connection.channel.direct.Session.Command> sshCommands = new ConcurrentHashMap<>();
    private static final Map<String, net.schmizz.sshj.connection.channel.direct.Session> sshSessions = new ConcurrentHashMap<>();

    /** 默认最大行数限制 */
    private static final int DEFAULT_MAX_LINES = 2000;

    @Autowired
    public void setSshManager(SshManager manager) {
        sshManager = manager;
    }

    @Autowired
    public void setInstanceMapper(ServiceInstanceMapper mapper) {
        instanceMapper = mapper;
    }

    @Autowired
    public void setServerMapper(ServerMapper mapper) {
        serverMapper = mapper;
    }

    /**
     * WebSocket 连接建立
     *
     * @param session    WebSocket 会话
     * @param instanceId 服务实例 ID
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("instanceId") Long instanceId) {
        String key = session.getId();
        sessions.put(key, session);

        // 解析查询参数
        Map<String, java.util.List<String>> params = session.getRequestParameterMap();
        String level = params.containsKey("level") ? params.get("level").get(0) : null;
        String maxLinesStr = params.containsKey("maxLines") ? params.get("maxLines").get(0) : null;
        String keyword = params.containsKey("keyword") ? params.get("keyword").get(0) : null;

        int maxLines = DEFAULT_MAX_LINES;
        if (maxLinesStr != null) {
            try {
                maxLines = Integer.parseInt(maxLinesStr);
            } catch (NumberFormatException e) {
                maxLines = DEFAULT_MAX_LINES;
            }
        }

        final int finalMaxLines = maxLines;
        log.info("WebSocket opened: {}, instanceId={}, level={}, maxLines={}, keyword={}",
                key, instanceId, level, maxLines, keyword);

        // 启动日志流
        new Thread(() -> startLogStreaming(key, instanceId, session, level, finalMaxLines, keyword)).start();
    }

    /**
     * WebSocket 连接关闭
     *
     * <p>清理资源，终止 tail 进程。</p>
     *
     * @param session WebSocket 会话
     */
    @OnClose
    public void onClose(Session session) {
        String key = session.getId();
        sessions.remove(key);

        // 终止 tail 进程
        net.schmizz.sshj.connection.channel.direct.Session.Command cmd = sshCommands.remove(key);
        if (cmd != null) {
            try {
                cmd.close();
                log.info("Terminated tail process for session: {}", key);
            } catch (IOException e) {
                log.warn("Failed to terminate tail process: {}", e.getMessage());
            }
        }

        // 关闭 SSH 会话
        net.schmizz.sshj.connection.channel.direct.Session sshSession = sshSessions.remove(key);
        if (sshSession != null) {
            try {
                sshSession.close();
            } catch (IOException e) {
                log.warn("Failed to close SSH session: {}", e.getMessage());
            }
        }

        // 关闭 SSH 连接
        SSHClient ssh = sshClients.remove(key);
        if (ssh != null) {
            try {
                ssh.close();
            } catch (IOException e) {
                log.warn("Failed to close SSH connection: {}", e.getMessage());
            }
        }

        log.info("WebSocket closed: {}", key);
    }

    /**
     * WebSocket 错误处理
     *
     * @param session WebSocket 会话
     * @param error   异常信息
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket error: {}", error.getMessage());
        onClose(session);
    }

    /**
     * 启动日志流
     *
     * @param key        会话标识
     * @param instanceId 服务实例 ID
     * @param session    WebSocket 会话
     * @param level      日志级别过滤（可选）
     * @param maxLines   最大行数限制
     * @param keyword    关键字过滤（可选）
     */
    private void startLogStreaming(String key, Long instanceId, Session session,
                                    String level, int maxLines, String keyword) {
        ServiceInstance inst = instanceMapper.selectById(instanceId);
        if (inst == null) {
            sendMessage(session, "服务实例不存在");
            return;
        }

        Server server = serverMapper.selectById(inst.getServerId());
        if (server == null) {
            sendMessage(session, "服务器不存在");
            return;
        }

        try {
            SSHClient ssh = sshManager.connect(server);
            sshClients.put(key, ssh);
            String logPath = inst.getDeployPath() + "/logs";

            // 构建 tail 命令
            String cmd;
            if (level != null && !level.isEmpty()) {
                // 使用 grep 过滤日志级别
                cmd = String.format("tail -n 500 -f %s/*.log 2>/dev/null | grep --line-buffered -i '%s'", logPath, level);
            } else {
                cmd = String.format("tail -n 500 -f %s/*.log 2>/dev/null", logPath);
            }

            net.schmizz.sshj.connection.channel.direct.Session sshSession = ssh.startSession();
            sshSessions.put(key, sshSession);
            net.schmizz.sshj.connection.channel.direct.Session.Command sshCmd = sshSession.exec(cmd);
            sshCommands.put(key, sshCmd);

            final int finalMaxLines = maxLines;
            int[] lineCount = {0};
            try (InputStream is = sshCmd.getInputStream()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null && session.isOpen() && lineCount[0] < finalMaxLines) {
                    // 关键字过滤
                    if (keyword != null && !keyword.isEmpty() && !line.toLowerCase().contains(keyword.toLowerCase())) {
                        continue;
                    }
                    sendMessage(session, line);
                    lineCount[0]++;
                }
            } catch (IOException e) {
                log.warn("Log streaming interrupted: {}", e.getMessage());
            } finally {
                try {
                    sshSession.close();
                } catch (IOException ignored) {
                }
                log.info("Log streaming ended for session: {}, lines={}", key, lineCount[0]);
            }
        } catch (Exception e) {
            sendMessage(session, "连接服务器失败: " + e.getMessage());
        }
    }

    /**
     * 发送 WebSocket 消息
     *
     * @param session WebSocket 会话
     * @param message 消息内容
     */
    private void sendMessage(Session session, String message) {
        try {
            if (session.isOpen()) {
                session.getBasicRemote().sendText(message);
            }
        } catch (IOException e) {
            log.warn("Failed to send WebSocket message: {}", e.getMessage());
        }
    }
}
