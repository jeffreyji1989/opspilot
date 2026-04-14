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
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.connection.channel.direct.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 实时日志 WebSocket 端点
 *
 * <p>通过 SSH 连接到目标服务器，使用 tail -f 实时推送日志到浏览器。</p>
 * <ul>
 *   <li>支持行数限制（默认 1000 行）</li>
 *   <li>WebSocket 关闭时自动终止 tail -f 进程</li>
 * </ul>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@Slf4j
@Component
@ServerEndpoint("/api/ws/log/{instanceId}")
public class LogWebSocket {

    private static SshManager sshManager;
    private static ServiceInstanceMapper instanceMapper;
    private static ServerMapper serverMapper;

    private static final Map<String, jakarta.websocket.Session> wsSessions = new ConcurrentHashMap<>();
    private static final Map<String, SSHClient> sshClients = new ConcurrentHashMap<>();
    private static final Map<String, Command> sshCmds = new ConcurrentHashMap<>();
    private static final Map<String, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();

    /** 默认最大日志行数 */
    private static final int DEFAULT_MAX_LINES = 1000;

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
     */
    @OnOpen
    public void onOpen(jakarta.websocket.Session session, @PathParam("instanceId") Long instanceId) {
        String key = session.getId();
        wsSessions.put(key, session);
        log.info("WebSocket opened: {}, instanceId={}", key, instanceId);
        new Thread(() -> startLogStreaming(key, instanceId, session)).start();
    }

    /**
     * WebSocket 连接关闭
     */
    @OnClose
    public void onClose(jakarta.websocket.Session session) {
        String key = session.getId();
        wsSessions.remove(key);

        AtomicBoolean stopFlag = stopFlags.remove(key);
        if (stopFlag != null) {
            stopFlag.set(true);
        }

        Command sshCmd = sshCmds.remove(key);
        if (sshCmd != null) {
            try {
                sshCmd.close();
                log.info("Terminated tail process for session {}", key);
            } catch (IOException ignored) {
            }
        }

        SSHClient ssh = sshClients.remove(key);
        if (ssh != null) {
            try {
                ssh.close();
            } catch (IOException ignored) {
            }
        }
        log.info("WebSocket closed: {}", key);
    }

    /**
     * WebSocket 错误处理
     */
    @OnError
    public void onError(jakarta.websocket.Session session, Throwable error) {
        log.error("WebSocket error: {}", error.getMessage());
        onClose(session);
    }

    /**
     * 接收客户端消息（预留）
     */
    @OnMessage
    public void onMessage(jakarta.websocket.Session session, String message) {
        log.debug("Received client message: {}", message);
    }

    /**
     * 启动日志推送
     */
    private void startLogStreaming(String key, Long instanceId, jakarta.websocket.Session session) {
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

        AtomicBoolean stopFlag = new AtomicBoolean(false);
        stopFlags.put(key, stopFlag);

        try {
            SSHClient ssh = sshManager.connect(server);
            sshClients.put(key, ssh);

            String logPath = inst.getDeployPath() + "/logs";
            String cmd = String.format("tail -n %d -f %s/*.log 2>/dev/null", DEFAULT_MAX_LINES, logPath);

            Session sshSession = ssh.startSession();
            Command sshCmd = sshSession.exec(cmd);
            sshCmds.put(key, sshCmd);

            try (InputStream is = sshCmd.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && session.isOpen() && !stopFlag.get()) {
                    sendMessage(session, line);
                }
            } catch (IOException e) {
                if (!stopFlag.get()) {
                    log.warn("Log streaming interrupted for session {}: {}", key, e.getMessage());
                }
            } finally {
                try {
                    sshSession.close();
                } catch (IOException ignored) {
                }
            }
        } catch (Exception e) {
            if (session.isOpen()) {
                sendMessage(session, "连接服务器失败: " + e.getMessage());
            }
        }
    }

    /**
     * 发送消息到客户端
     */
    private void sendMessage(jakarta.websocket.Session session, String message) {
        try {
            if (session.isOpen()) {
                session.getBasicRemote().sendText(message);
            }
        } catch (IOException e) {
            log.warn("Failed to send WebSocket message: {}", e.getMessage());
        }
    }
}
