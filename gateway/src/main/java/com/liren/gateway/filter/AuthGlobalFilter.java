package com.liren.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liren.api.problem.api.user.UserInterface;
import com.liren.api.problem.dto.user.UserPasswordVersionDTO;
import com.liren.common.core.constant.Constants;
import com.liren.common.core.result.Result;
import com.liren.common.core.result.ResultCode;
import com.liren.common.core.utils.JwtUtil;
import com.liren.common.redis.RedisUtil;
import com.liren.gateway.properties.AuthWhiteList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Lazy;
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

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    @Lazy
    private UserInterface userInterface;

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    // 白名单接口 (无需登录即可访问)
    @Autowired
    private AuthWhiteList whitelist;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        log.info("AuthGlobalFilter: 请求路径={}", path);

        // 1. 获取 Token
        String token = request.getHeaders().getFirst("Authorization");
        if (!StringUtils.hasText(token)) {
            token = request.getHeaders().getFirst("token");
        }
        if (StringUtils.hasText(token) && token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7);
        }

        // 2. 白名单 + 无 token：直接放行，不需要透传信息
        boolean isWhiteList = isWhitelist(path);
        if (isWhiteList && !StringUtils.hasText(token)) {
            log.info("AuthGlobalFilter: 白名单 + 无token：直接放行");
            return chain.filter(exchange);
        }

        // 3. token不存在或为空
        if (!StringUtils.hasText(token)) {
            log.warn("AuthGlobalFilter: token无效或不存在");
            return unauthorizedResponse(exchange, ResultCode.UNAUTHORIZED);
        }

        // 到这 token 肯定是存在的
        // 4. 先校验 Redis 登录态缓存：user:login:{token} -> userId
        String loginCacheKey = Constants.USER_LOGIN_CACHE_PREFIX + token;
        String userIdStr = redisUtil.get(loginCacheKey, String.class);

        if (!StringUtils.hasText(userIdStr)) {
            // 如果是白名单接口，Token 无效时应该放行（当做游客），而不是报错
            if (isWhiteList) {
                log.info("AuthGlobalFilter: 白名单接口Token失效，降级为游客访问");
                // 清除可能存在的脏 Header，防止下游误判
                ServerHttpRequest.Builder builder = request.mutate();
                builder.headers(headers -> {
                    headers.remove("userId");
                    headers.remove("userRole");
                });
                return chain.filter(exchange.mutate().request(builder.build()).build());
            }

            // 如果不是白名单，且userId是无效的，则直接禁止
            log.warn("AuthGlobalFilter: redis缓存中的token不存在或过期");
            return unauthorizedResponse(exchange, ResultCode.UNAUTHORIZED);
        }

        // 5. 刷新用户登录过期时间
        redisUtil.expire(loginCacheKey, Constants.USER_LOGIN_CACHE_EXPIRE_TIME);
        log.info("AuthGlobalFilter: 刷新登录缓存TTL，userId={}", userIdStr);

        // 6. 校验密码版本号，所以先拿到jwt中的版本号
        Long jwtPasswordVersion = jwtUtil.getPasswordVersion(token);
        if (jwtPasswordVersion == null) {
            log.warn("AuthGlobalFilter: JWT中缺少passwordVersion");
            return unauthorizedResponse(exchange, ResultCode.UNAUTHORIZED);
        }

        // 7. 再从缓存/数据库获取当前用户的 passwordVersion
        String versionCacheKey = Constants.USER_PASSWORD_VERSION_CACHE_PREFIX + userIdStr;
        String cachedVersionStr = redisUtil.get(versionCacheKey, String.class);
        Long currentVersion;

        if (cachedVersionStr != null) {
            currentVersion = Long.valueOf(cachedVersionStr);
        } else {
            // 调用 User Service 查询
            Result<UserPasswordVersionDTO> result = userInterface.getPasswordVersion(Long.parseLong(userIdStr));
            if (!result.isSuccess(result) || result.getData() == null) {
                log.warn("AuthGlobalFilter: 用户不存在，userId={}", userIdStr);
                return unauthorizedResponse(exchange, ResultCode.UNAUTHORIZED);
            }

            UserPasswordVersionDTO user = result.getData();
            currentVersion = user.getPasswordVersion() != null ? user.getPasswordVersion() : 0L;
            redisUtil.set(versionCacheKey, String.valueOf(currentVersion), Constants.USER_LOGIN_CACHE_EXPIRE_TIME);
        }

        // 8. 比对版本号（JWT中的版本号 必须等于 当前用户的版本号）
        if (!jwtPasswordVersion.equals(currentVersion)) {
            log.warn("AuthGlobalFilter: 密码版本号不匹配，jwtVersion={}, currentVersion={}, 可能已重置密码",
                    jwtPasswordVersion, currentVersion);

            redisUtil.del(loginCacheKey); // 删除失效的登录态缓存
            redisUtil.del(versionCacheKey); // 删除版本号缓存（强制下次重新查询）
            return unauthorizedResponse(exchange, ResultCode.UNAUTHORIZED);
        }
        log.info("AuthGlobalFilter: 密码版本号校验通过，userId={}, version={}", userIdStr, currentVersion);

        // 9. 解析 JWT 中的 role（辅助信息，失败不影响登录）
        String userRole = null;
        try {
            userRole = jwtUtil.getUserRole(token);
        } catch (Exception e) {
            log.warn("AuthGlobalFilter: Failed to parse userRole from JWT", e);
        }

        // 10. 透传身份信息（防 Header 注入）
        ServerHttpRequest.Builder builder = request.mutate();
        builder.headers(headers -> {
            headers.remove("userId");
            headers.remove("userRole");
        });
        builder.header("userId", userIdStr);
        if (StringUtils.hasText(userRole)) {
            builder.header("userRole", userRole);
        }

        log.info("AuthGlobalFilter: Pass with userId={}, role={}", userIdStr, userRole);
        return chain.filter(exchange.mutate().request(builder.build()).build());
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