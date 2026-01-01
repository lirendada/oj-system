package com.liren.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liren.common.core.result.Result;
import com.liren.common.core.result.ResultCode;
import com.liren.common.core.utils.JwtUtil;
import com.liren.gateway.properties.AuthWhiteList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关全局鉴权过滤器
 * 作用：拦截所有请求，校验 JWT Token，将 UserId 传递给下游服务
 */
@Component
@Slf4j
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    // 白名单接口 (无需登录即可访问)
    @Autowired
    private AuthWhiteList whitelist;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        log.info("AuthGlobalFilter: path={}", path);

        // 1. 白名单校验：如果是白名单接口，直接放行
        if (isWhitelist(path)) {
            log.info("AuthGlobalFilter: path is in whitelist, pass");
            return chain.filter(exchange);
        }

        // 2. 获取 Token
        // 约定：前端把 Token 放在 Header 的 "Authorization" 字段，或者 "token" 字段
        String token = request.getHeaders().getFirst("Authorization");
        if (!StringUtils.hasText(token)) {
            // 尝试从 token 字段取
            token = request.getHeaders().getFirst("token");
        }

        // 3. 校验 Token
        Long userId = null;
        if (StringUtils.hasText(token)) {
            // 这里的 JwtUtils 就是我们之前在 common-core 里写的
            // 注意：如果 token 带了 "Bearer " 前缀，需要去掉
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            userId = JwtUtil.getUserId(token);
        }

        // 4. Token 无效或解析失败，拦截请求
        if (userId == null) {
            return unauthorizedResponse(exchange, ResultCode.UNAUTHORIZED);
        }

        // 5. Token 有效，透传 UserId 到下游微服务
        // 关键步骤：把 userId 放入 Header，这样下游的 System/User 服务就能知道是谁在请求了
        ServerHttpRequest mutableReq = request.mutate()
                .header("userId", String.valueOf(userId))
                .build();

        return chain.filter(exchange.mutate().request(mutableReq).build());
    }

    /**
     * 检查是否在白名单中
     */
    private boolean isWhitelist(String path) {
        List<String> list = whitelist.getWhitelist();
        if(list != null) {
            for (String pattern : list) {
                if (antPathMatcher.match(pattern, path)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 返回 401 未授权的 JSON 响应
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, ResultCode resultCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK); // 这里通常返回 200，具体错误码在 Body 里体现，看你前端约定
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

        Result<?> failResult = Result.fail(resultCode.getCode(), resultCode.getMessage());
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(failResult);
        } catch (JsonProcessingException e) {
            bytes = "{\"code\": 401, \"msg\": \"未授权\"}".getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 优先级设置：数字越小优先级越高
        return -1;
    }
}