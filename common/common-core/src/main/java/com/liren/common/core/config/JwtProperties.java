package com.liren.common.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@Data
@RefreshScope
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    /**
     * JWT 密钥（从配置中心读取）
     */
    private String secret;

    /**
     * Token 过期时间（毫秒）
     */
    private Long expiration;
}
