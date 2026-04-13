package com.opspilot.common;

import com.opspilot.entity.Server;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.userauth.password.PasswordUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Component
public class SshManager {

    @Value("${opspilot.ssh.private-key-path}")
    private String privateKeyPath;

    public SSHClient connect(Server server) throws IOException {
        return connect(server, null);
    }

    public SSHClient connect(Server server, String password) throws IOException {
        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(server.getHostname(), server.getPort());

        try {
            if (password != null && !password.isEmpty()) {
                ssh.authPassword(server.getSshUsername() != null ? server.getSshUsername() : server.getHostname(), password);
            } else {
                // Use private key authentication
                File keyFile = new File(privateKeyPath);
                if (!keyFile.exists()) {
                    throw new BusinessException("SSH私钥文件不存在: " + privateKeyPath);
                }
                KeyProvider kp = ssh.loadKeys(privateKeyPath);
                ssh.authPublickey(server.getSshUsername() != null ? server.getSshUsername() : server.getHostname(), kp);
            }
            log.info("SSH connected to {}@{}:{}", server.getSshUsername(), server.getHostname(), server.getPort());
            return ssh;
        } catch (Exception e) {
            ssh.close();
            throw new BusinessException("SSH连接失败: " + e.getMessage());
        }
    }

    public String executeCommand(SSHClient ssh, String command, int timeoutSeconds) throws IOException {
        try (Session session = ssh.startSession()) {
            Session.Command cmd = session.exec(command);
            String output = IOUtils.readFully(cmd.getInputStream()).toString();
            cmd.join(timeoutSeconds, TimeUnit.SECONDS);
            if (cmd.getExitStatus() != null && cmd.getExitStatus() != 0) {
                String error = IOUtils.readFully(cmd.getErrorStream()).toString();
                log.warn("Command exited with status {}: {}, error: {}", cmd.getExitStatus(), command, error);
            }
            return output;
        }
    }

    public void executeCommandAsync(SSHClient ssh, String command, Consumer<String> lineConsumer) throws IOException {
        Session session = ssh.startSession();
        Session.Command cmd = session.exec(command);
        try (InputStream is = cmd.getInputStream()) {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                lineConsumer.accept(line);
            }
        } catch (Exception e) {
            log.warn("Async command interrupted: {}", e.getMessage());
        } finally {
            try { session.close(); } catch (IOException ignored) {}
        }
    }

    public void setupSshTrust(Server server, String password, String publicKey) throws IOException {
        SSHClient ssh = connect(server, password);
        try {
            String user = server.getSshUsername() != null ? server.getSshUsername() : "root";
            // Ensure .ssh directory exists
            executeCommand(ssh, "mkdir -p ~" + user + "/.ssh && chmod 700 ~" + user + "/.ssh", 10);
            // Append public key to authorized_keys
            executeCommand(ssh, "echo '" + publicKey + "' >> ~" + user + "/.ssh/authorized_keys && chmod 600 ~" + user + "/.ssh/authorized_keys", 10);
            // Verify connection with key
            SSHClient verify = connect(server, null);
            verify.close();
            log.info("SSH trust established for server: {}", server.getServerName());
        } finally {
            ssh.close();
        }
    }

    /**
     * Execute command on server via SSH (or locally if server is null)
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
            log.error("SSH命令执行失败: {}, 错误: {}", command, e.getMessage());
            throw new RuntimeException("SSH命令执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * Upload file to remote server via SCP
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
            log.error("文件上传失败: {}, 错误: {}", localPath, e.getMessage());
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * Execute command locally
     */
    private String executeLocalCommand(String command, int timeoutSeconds) throws IOException {
        log.info("执行本地命令: {}", command);
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
        // Generate SSH key pair using ssh-keygen
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
        // Read public key
        String pubKey = Files.readString(Paths.get(outputPath + ".pub"));
        return pubKey;
    }
}
