package com.liren.common.web.interceptor;

import com.liren.common.core.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
public class UserInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从 Header 中获取 userId (网关传过来的)
        String userIdStr = request.getHeader("userId");

        // 2. 如果有 userId，存入 ThreadLocal
        if (StringUtils.hasText(userIdStr)) {
            try {
                Long userId = Long.valueOf(userIdStr);
                UserContext.setUserId(userId);
            } catch (NumberFormatException e) {
                log.error("解析 Header 中的 userId 失败: {}", userIdStr);
            }
        }

        // 3. 从 Header 中获取 userRole
        String userRole = request.getHeader("userRole");
        if (StringUtils.hasText(userRole)) {
            UserContext.setUserRole(userRole);
        }

        // 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 【非常重要】请求结束后，必须清理 ThreadLocal，防止内存泄漏和线程复用导致的数据污染
        UserContext.remove();
    }
}
