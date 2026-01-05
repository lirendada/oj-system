package com.liren.common.core.utils;

import com.liren.common.core.constant.Constants;
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
    private static final Key key = Keys.hmacShaKeyFor(Constants.JWT_SECRET.getBytes());

    /**
     * 生成 Token
     * @param userId 用户ID
     * @return Token 字符串
     */
    public static String createToken(Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId); // 统一 Key 为 "userId"

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId)) // 同时设置 Subject，方便标准解析
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + Constants.TOKEN_EXPIRATION_TIME))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 生成带额外信息的 Token (例如昵称、角色等)
     */
    public static String createToken(Long userId, Map<String, Object> claims) {
        if (claims == null) {
            claims = new HashMap<>();
        }
        claims.put("userId", userId);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + Constants.TOKEN_EXPIRATION_TIME))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 Token 获取 Claims
     */
    public static Claims parseToken(String token) {
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
    public static Long getUserId(String token) {
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
    public static String getUserRole(String token) {
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
    public static boolean validateToken(String token) {
        return getUserId(token) != null;
    }
}