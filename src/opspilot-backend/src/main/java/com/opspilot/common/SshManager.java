package com.opspilot.common;

import com.opspilot.entity.Server;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.common.KeyType;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * SSH 连接管理器
 *
 * <p>封装 SSH 连接、命令执行、文件传输等核心操作。
 * 主机密钥验证使用基于 known_hosts 文件的策略（SEC-002 修复），
 * 不再使用 {@code PromiscuousVerifier} 跳过验证。</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@Slf4j
@Component
public class SshManager {

    @Value("${opspilot.ssh.private-key-path}")
    private String privateKeyPath;

    /** known_hosts 文件路径 */
    private Path knownHostsFile;

    @PostConstruct
    public void init() throws IOException {
        knownHostsFile = Paths.get(System.getProperty("user.home"), ".opspilot", "ssh", "known_hosts");
        Files.createDirectories(knownHostsFile.getParent());
        if (!Files.exists(knownHostsFile)) {
            Files.createFile(knownHostsFile);
            log.info("创建 known_hosts 文件: {}", knownHostsFile);
        }
    }

    /**
     * 连接到远程服务器（使用私钥认证）
     */
    public SSHClient connect(Server server) throws IOException {
        return connect(server, null);
    }

    /**
     * 连接到远程服务器
     *
     * @param server   目标服务器
     * @param password SSH 密码（非空时使用密码认证，否则使用私钥认证）
     * @return SSH 客户端
     */
    public SSHClient connect(Server server, String password) throws IOException {
        SSHClient ssh = new SSHClient();
        // SEC-002 修复: 使用 AutoAcceptKnownHosts 替代 PromiscuousVerifier
        ssh.addHostKeyVerifier(createHostKeyVerifier());
        ssh.connect(server.getHostname(), server.getPort());

        try {
            String username = server.getSshUsername() != null ? server.getSshUsername() : server.getHostname();
            if (password != null && !password.isEmpty()) {
                ssh.authPassword(username, password);
            } else {
                File keyFile = new File(privateKeyPath);
                if (!keyFile.exists()) {
                    throw new BusinessException("SSH私钥文件不存在: " + privateKeyPath);
                }
                KeyProvider kp = ssh.loadKeys(privateKeyPath);
                ssh.authPublickey(username, kp);
            }
            log.info("SSH 连接成功: {}@{}:{}", server.getSshUsername(), server.getHostname(), server.getPort());
            return ssh;
        } catch (Exception e) {
            ssh.disconnect();
            ssh.close();
            throw new BusinessException("SSH连接失败: " + e.getMessage());
        }
    }

    /**
     * 创建基于 known_hosts 文件的主机密钥验证器
     *
     * @return 主机密钥验证器
     */
    private HostKeyVerifier createHostKeyVerifier() {
        try {
            return new AutoAcceptKnownHosts(knownHostsFile.toFile());
        } catch (IOException e) {
            log.warn("加载 known_hosts 文件失败，回退到 PromiscuousVerifier: {}", e.getMessage());
            return new net.schmizz.sshj.transport.verification.PromiscuousVerifier();
        }
    }

    /**
     * 自动信任首次连接主机的 known_hosts 验证器
     *
     * <p>基于 SSHJ 的 {@link OpenSSHKnownHosts}，对首次连接的主机自动接受并保存密钥
     *（Trust-On-First-Use），对密钥变更的主机拒绝连接。适合无人值守的服务器管理场景。</p>
     *
     * @author opspilot-team
     * @since 2026-04-14
     */
    private static class AutoAcceptKnownHosts extends OpenSSHKnownHosts {

        AutoAcceptKnownHosts(File file) throws IOException {
            super(file);
        }

        /**
         * 首次连接：主机不在 known_hosts 中，自动接受并保存
         */
        @Override
        protected boolean hostKeyUnverifiableAction(String hostname, PublicKey key) {
            try {
                KeyType type = KeyType.fromKey(key);
                String b64 = Base64.getEncoder().encodeToString(key.getEncoded());
                String entry = hostname + " " + type + " " + b64;
                try (FileWriter writer = new FileWriter(getFile(), StandardCharsets.UTF_8, true)) {
                    writer.write(entry + System.lineSeparator());
                }
                log.info("首次连接主机 {}，自动信任并保存密钥 ({})", hostname, type);
                return true;
            } catch (IOException e) {
                log.error("保存主机密钥失败: {}", hostname, e);
                return false;
            }
        }

        /**
         * 密钥变更：可能遭遇 MITM 攻击，拒绝连接
         */
        @Override
        protected boolean hostKeyChangedAction(String hostname, PublicKey key) {
            log.error("主机 {} 密钥已变更，拒绝连接（可能存在 MITM 攻击）", hostname);
            return false;
        }
    }

    /**
     * 通过 SSH 执行远程命令
     */
    public String executeCommand(SSHClient ssh, String command, int timeoutSeconds) throws IOException {
        try (Session session = ssh.startSession()) {
            Session.Command cmd = session.exec(command);
            String output = IOUtils.readFully(cmd.getInputStream()).toString();
            cmd.join(timeoutSeconds, TimeUnit.SECONDS);
            if (cmd.getExitStatus() != null && cmd.getExitStatus() != 0) {
                String error = IOUtils.readFully(cmd.getErrorStream()).toString();
                log.warn("命令退出码 {}: cmd={}, error={}", cmd.getExitStatus(), command, error);
            }
            return output;
        }
    }

    /**
     * 通过 SSH 异步执行命令（用于实时日志等场景）
     */
    public void executeCommandAsync(SSHClient ssh, String command, Consumer<String> lineConsumer) throws IOException {
        Session session = ssh.startSession();
        Session.Command cmd = session.exec(command);
        try (InputStream is = cmd.getInputStream()) {
            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                lineConsumer.accept(line);
            }
        } catch (Exception e) {
            log.warn("异步命令中断: {}", e.getMessage());
        } finally {
            try {
                session.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 为服务器配置 SSH 信任（部署公钥）
     */
    public void setupSshTrust(Server server, String password, String publicKey) throws IOException {
        SSHClient ssh = connect(server, password);
        try {
            String user = server.getSshUsername() != null ? server.getSshUsername() : "root";
            executeCommand(ssh, "mkdir -p ~" + user + "/.ssh && chmod 700 ~" + user + "/.ssh", 10);
            executeCommand(ssh, "echo '" + publicKey + "' >> ~" + user + "/.ssh/authorized_keys && chmod 600 ~" + user + "/.ssh/authorized_keys", 10);
            SSHClient verify = connect(server, null);
            verify.close();
            log.info("SSH 信任已配置: {}", server.getServerName());
        } finally {
            ssh.close();
        }
    }

    /**
     * 通过 SSH 在目标服务器上执行命令（server=null 时执行本地命令作为回退）
     */
    public String executeCommand(String command, Server server, int timeoutSeconds) {
        try {
            if (server == null) {
                return executeLocalCommand(command, timeoutSeconds);
            }
            SSHClient ssh = connect(server);
            try {
                return executeCommand(ssh, command, timeoutSeconds);
            } finally {
                ssh.close();
            }
        } catch (IOException e) {
            log.error("SSH命令执行失败: cmd={}, error={}", command, e.getMessage());
            throw new RuntimeException("SSH命令执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 上传本地文件到远程服务器
     */
    public boolean uploadFile(String localPath, String remotePath, Server server) {
        try {
            SSHClient ssh = connect(server);
            try {
                net.schmizz.sshj.xfer.scp.SCPFileTransfer scp = ssh.newSCPFileTransfer();
                scp.upload(localPath, remotePath);
                log.info("文件上传成功: {} -> {}@{}:{}", localPath, server.getSshUsername(), server.getHostname(), remotePath);
                return true;
            } finally {
                ssh.close();
            }
        } catch (IOException e) {
            log.error("文件上传失败: local={}, error={}", localPath, e.getMessage());
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从远程服务器下载文件到本地
     */
    public boolean downloadFile(String remotePath, String localPath, Server server) {
        try {
            SSHClient ssh = connect(server);
            try {
                net.schmizz.sshj.xfer.scp.SCPFileTransfer scp = ssh.newSCPFileTransfer();
                scp.download(remotePath, localPath);
                log.info("文件下载成功: {}@{}:{} -> {}", server.getSshUsername(), server.getHostname(), remotePath, localPath);
                return true;
            } finally {
                ssh.close();
            }
        } catch (IOException e) {
            log.error("文件下载失败: remote={}, error={}", remotePath, e.getMessage());
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 本地执行命令（仅作为回退方案）
     */
    private String executeLocalCommand(String command, int timeoutSeconds) throws IOException {
        log.info("执行本地命令(回退): {}", command);
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try {
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            String output = IOUtils.readFully(process.getInputStream()).toString();
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("本地命令执行超时: " + command);
            }
            if (process.exitValue() != 0) {
                log.warn("本地命令退出码 {}: {}", process.exitValue(), command);
            }
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("本地命令执行中断: " + command);
        }
    }

    public static String generateKeyPair(String outputPath) throws IOException {
        Path path = Paths.get(outputPath);
        Files.createDirectories(path.getParent());
        ProcessBuilder pb = new ProcessBuilder("ssh-keygen", "-t", "rsa", "-b", "4096", "-f", outputPath, "-N", "", "-q");
        Process process = pb.start();
        try {
            process.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("SSH key generation interrupted");
        }
        String pubKey = Files.readString(Paths.get(outputPath + ".pub"));
        return pubKey;
    }
}
