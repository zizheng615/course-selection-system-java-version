package com.example.cs.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析工具。使用 HS256 对称签名。
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-hours:24}")
    private long expireHours;

    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("jwt.secret 长度不足 32 字节，无法用于 HS256 签名");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /** 签发 Token，把登录用户的关键信息写入 claims，避免每次请求都查库。 */
    public String generate(LoginUser user) {
        long now = System.currentTimeMillis();
        Date expiration = new Date(now + expireHours * 3600_000L);
        return Jwts.builder()
                .subject(String.valueOf(user.userId()))
                .claim("username", user.username())
                .claim("role", user.role())
                .claim("realName", user.realName())
                .claim("refId", user.refId())
                .issuedAt(new Date(now))
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    /**
     * 解析 Token。
     *
     * @return 解析成功返回登录用户；Token 非法或过期返回 null
     */
    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Number refId = claims.get("refId", Number.class);
            return new LoginUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("role", String.class),
                    claims.get("realName", String.class),
                    refId == null ? null : refId.longValue());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    public long getExpireHours() {
        return expireHours;
    }
}
