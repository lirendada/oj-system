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
    @Autowired
    private JwtUtil jwtUtil;

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    // 白名单接口 (无需登录即可访问)
    @Autowired
    private AuthWhiteList whitelist;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        log.info("AuthGlobalFilter: Request path={}", path);

        // 1. 获取 Token
        String token = request.getHeaders().getFirst("Authorization");
        if (!StringUtils.hasText(token)) {
            token = request.getHeaders().getFirst("token");
        }
        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 2. 尝试解析 Token
        Long userId = null;
        String userRole = null;

        if (StringUtils.hasText(token)) {
            // 如果带了 Token，就尝试解析
            userId = jwtUtil.getUserId(token);
            userRole = jwtUtil.getUserRole(token);
        }

        // 3. 鉴权逻辑
        if (userId == null) {
            // 情况 A：没带 Token，或者 Token 无效
            if (isWhitelist(path)) {
                // 如果是白名单接口，允许游客访问 -> 直接放行 (不透传 Header)
                return chain.filter(exchange);
            } else {
                // 如果不是白名单，必须登录 -> 拦截报错
                return unauthorizedResponse(exchange, ResultCode.UNAUTHORIZED);
            }
        }

        // 4. Token 有效 -> 透传身份信息
        // 走到这里说明 userId 一定有值，不管是不是白名单，都把身份传下去
        ServerHttpRequest.Builder builder = request.mutate()
                .header("userId", String.valueOf(userId));

        if (StringUtils.hasText(userRole)) {
            builder.header("userRole", userRole);
        }

        log.info("AuthGlobalFilter: Pass with userId={}, role={}", userId, userRole);
        ServerHttpRequest mutableReq = builder.build();

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