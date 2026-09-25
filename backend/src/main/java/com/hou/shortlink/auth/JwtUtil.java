package com.hou.shortlink.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类（jjwt 0.12.x 新 API）
 * JWT 三段结构：Header.Payload.Signature（base64 拼接）
 * 职责只有两个：生成 token、解析 token（解析时顺便完成签名和过期校验）
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire-days}") int expireDays) {
        // HMAC-SHA256 要求密钥至少 32 字节，太短会直接抛异常
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireDays * 24L * 60 * 60 * 1000;
    }

    /** 生成 token：subject 里放用户 id，它是我们解析时唯一需要的身份信息 */
    public String generate(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token 并返回用户 id
     * 签名不对或已过期会抛 JwtException 子类异常，由调用方（拦截器）统一处理成 401
     */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)          // 校验签名，防止 token 被篡改
                .build()
                .parseSignedClaims(token) // 同时校验过期时间
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }
}
