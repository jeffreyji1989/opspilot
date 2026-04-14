package com.opspilot.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加密/解密工具类
 *
 * <p>用于加密 Git 凭证等敏感数据。密钥通过 {@code opspilot.aes.key} 配置项注入，
 * 启动时校验密钥是否为默认值，防止生产环境使用不安全密钥（SEC-001 修复）。</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@Slf4j
@Component
public class AesGcmUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static byte[] AES_KEY;

    /**
     * 默认 AES 密钥占位符，启动时校验必须被覆盖。
     * 请通过环境变量 {@code OPSPILOT_AES_KEY} 注入真实密钥。
     */
    private static final String DEFAULT_AES_KEY = "DefaultAesKey16Ch";

    @Value("${opspilot.aes.key}")
    private String aesKeyConfig;

    /**
     * 启动时校验 AES 密钥是否为默认值
     *
     * <p>如果密钥仍为默认值，说明未通过环境变量覆盖，
     * 此时拒绝启动以避免生产环境使用不安全密钥。</p>
     */
    @PostConstruct
    public void validateAesKey() {
        if (DEFAULT_AES_KEY.equals(aesKeyConfig)) {
            throw new IllegalStateException(
                    "AES 密钥仍为默认值，存在安全风险！"
                            + "请通过环境变量 OPSPILOT_AES_KEY 注入不少于 32 字节的密钥。"
                            + "示例: export OPSPILOT_AES_KEY=$(openssl rand -base64 32)");
        }
        byte[] keyBytes = aesKeyConfig.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length == 16) {
            AES_KEY = new byte[32];
            System.arraycopy(keyBytes, 0, AES_KEY, 0, 16);
            System.arraycopy(keyBytes, 0, AES_KEY, 16, 16);
        } else if (keyBytes.length >= 32) {
            AES_KEY = new byte[32];
            System.arraycopy(keyBytes, 0, AES_KEY, 0, 32);
        } else {
            AES_KEY = new byte[32];
            System.arraycopy(keyBytes, 0, AES_KEY, 0, keyBytes.length);
        }
        log.info("AES-256-GCM 密钥初始化成功");
    }

    /**
     * AES-256-GCM 加密
     *
     * @param plaintext 明文
     * @return Base64 编码的密文（IV + 密文）
     */
    public static String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"), spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new BusinessException("数据加密失败: " + e.getMessage());
        }
    }

    /**
     * AES-256-GCM 解密
     *
     * @param encrypted Base64 编码的密文（IV + 密文）
     * @return 明文
     */
    public static String decrypt(String encrypted) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);

            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"), spec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("数据解密失败: " + e.getMessage());
        }
    }
}
