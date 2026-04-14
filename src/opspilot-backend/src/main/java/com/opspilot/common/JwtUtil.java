package com.opspilot.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 *
 * <p>用于生成和解析用户认证 Token。密钥通过 {@code opspilot.jwt.secret} 配置项注入，
 * 启动时校验密钥是否为默认值，防止生产环境使用不安全密钥（SEC-001 修复）。</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@Slf4j
@Component
public class JwtUtil {

    /**
     * 默认 JWT 密钥占位符，启动时校验必须被覆盖。
     * 请通过环境变量 {@code OPSPILOT_JWT_SECRET} 注入真实密钥。
     */
    private static final String DEFAULT_JWT_SECRET = "OpsPilotSecretKey2024ChangeMeInProduction";

    @Value("${opspilot.jwt.secret}")
    private String secret;

    @Value("${opspilot.jwt.expiration}")
    private long expiration;

    /**
     * 启动时校验 JWT 密钥是否为默认值
     *
     * <p>如果密钥仍为默认值，说明未通过环境变量覆盖，
     * 此时拒绝启动以避免生产环境使用不安全密钥。</p>
     */
    @PostConstruct
    public void validateJwtSecret() {
        if (DEFAULT_JWT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "JWT 密钥仍为默认值，存在安全风险！"
                            + "请通过环境变量 OPSPILOT_JWT_SECRET 注入不少于 32 字节的密钥。"
                            + "示例: export OPSPILOT_JWT_SECRET=$(openssl rand -base64 64)");
        }
        log.info("JWT 密钥初始化成功");
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 JWT Token
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @return JWT Token 字符串
     */
    public String generateToken(Long userId, String username) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 JWT Token
     *
     * @param token JWT Token 字符串
     * @return Claims 载荷
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中获取用户 ID
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }

    /**
     * 从 Token 中获取用户名
     */
    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * 检查 Token 是否已过期
     */
    public boolean isTokenExpired(String token) {
        try {
            return parseToken(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
