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

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ServerEndpoint("/api/ws/log/{instanceId}")
public class LogWebSocket {

    private static SshManager sshManager;
    private static ServiceInstanceMapper instanceMapper;
    private static ServerMapper serverMapper;

    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private static final Map<String, SSHClient> sshClients = new ConcurrentHashMap<>();

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

    @OnOpen
    public void onOpen(Session session, @PathParam("instanceId") Long instanceId) {
        String key = session.getId();
        sessions.put(key, session);
        log.info("WebSocket opened: {}, instanceId={}", key, instanceId);

        // Start tail -f
        new Thread(() -> startLogStreaming(key, instanceId, session)).start();
    }

    @OnClose
    public void onClose(Session session) {
        String key = session.getId();
        sessions.remove(key);
        SSHClient ssh = sshClients.remove(key);
        if (ssh != null) {
            try { ssh.close(); } catch (IOException ignored) {}
        }
        log.info("WebSocket closed: {}", key);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket error: {}", error.getMessage());
        onClose(session);
    }

    private void startLogStreaming(String key, Long instanceId, Session session) {
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
            String cmd = String.format("tail -n 500 -f %s/*.log 2>/dev/null", logPath);

            net.schmizz.sshj.connection.channel.direct.Session sshSession = ssh.startSession();
            net.schmizz.sshj.connection.channel.direct.Session.Command sshCmd = sshSession.exec(cmd);

            try (InputStream is = sshCmd.getInputStream()) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                String line;
                while ((line = reader.readLine()) != null && session.isOpen()) {
                    sendMessage(session, line);
                }
            } catch (IOException e) {
                log.warn("Log streaming interrupted: {}", e.getMessage());
            } finally {
                try { sshSession.close(); } catch (IOException ignored) {}
            }
        } catch (Exception e) {
            sendMessage(session, "连接服务器失败: " + e.getMessage());
        }
    }

    private void sendMessage(Session session, String message) {
        try {
            session.getBasicRemote().sendText(message);
        } catch (IOException e) {
            log.warn("Failed to send WebSocket message: {}", e.getMessage());
        }
    }
}
