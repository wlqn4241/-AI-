package com.psych.ai.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/** JWT 工具：无状态登录鉴权（优化点：替代传统 Session，便于横向扩展） */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-hours:72}")
    private long expireHours;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Long userId, String username) {
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(expireHours, ChronoUnit.HOURS)))
                .signWith(key())
                .compact();
    }

    /** 解析 token，返回用户 ID；非法/过期抛异常 */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(token).getPayload();
        Object uid = claims.get("uid");
        if (uid instanceof Number n) {
            return n.longValue();
        }
        throw new RuntimeException("非法 token");
    }
}
