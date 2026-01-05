package com.liren.common.core.utils;

import com.liren.common.core.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class JwtUtil {

    private Key key;
    private Long expiration;

    /**
     * 初始化密钥和过期时间
     */
    public JwtUtil(JwtProperties jwtProperties) {
        String secret = jwtProperties.getSecret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("JWT 密钥未配置，请在 Nacos 中配置 jwt.secret");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.expiration = jwtProperties.getExpiration();
        if (this.expiration == null) {
            this.expiration = 86400000L; // 默认 24 小时
        }
        log.info("JWT 配置初始化完成，过期时间: {} ms", this.expiration);
    }


    /**
     * 生成 Token
     */
    public String createToken(Long userId) {
        // 需要改为实例方法
        return createToken(userId, null);
    }

    /**
     * 生成带额外信息的 Token (例如昵称、角色等)
     */
    public String createToken(Long userId, Map<String, Object> claims) {
        if (claims == null) {
            claims = new HashMap<>();
        }
        claims.put("userId", userId);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 Token 获取 Claims
     */
    public Claims parseToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.error("Token解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析 Token 获取用户ID (核心方法)
     * 返回 Long 类型，防止雪花算法 ID 精度丢失
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        // 兼容性处理：无论存的时候是 Integer 还是 Long，都转 String 再转 Long
        Object userIdObj = claims.get("userId");
        if (userIdObj == null) {
            // 尝试从 Subject 获取
            String subject = claims.getSubject();
            if (StringUtils.hasText(subject)) {
                return Long.valueOf(subject);
            }
            return null;
        }
        return Long.valueOf(userIdObj.toString());
    }

    /**
     * 【新增】解析 Token 获取用户角色
     */
    public String getUserRole(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        Object roleObj = claims.get("userRole");
        return roleObj != null ? roleObj.toString() : null;
    }

    /**
     * 校验 Token 是否有效
     */
    public boolean validateToken(String token) {
        return getUserId(token) != null;
    }
}