package com.liren.common.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 用户上下文工具类
 * 基于 Alibaba TransmittableThreadLocal，实现全链路/异步线程的用户ID传递
 */
public class UserContext {

    // 核心：使用 TransmittableThreadLocal 而不是普通的 ThreadLocal
    private static final ThreadLocal<Long> USER_ID = new TransmittableThreadLocal<>();

    // 用户角色 (例如 "admin", "user")
    private static final ThreadLocal<String> USER_ROLE = new TransmittableThreadLocal<>();

    /**
     * 设置当前登录用户ID
     */
    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /**
     * 获取当前登录用户ID
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    // 【新增】设置角色
    public static void setUserRole(String role) {
        USER_ROLE.set(role);
    }

    // 【新增】获取角色
    public static String getUserRole() {
        return USER_ROLE.get();
    }

    /**
     * 清除上下文
     */
    public static void remove() {
        USER_ID.remove();
        USER_ROLE.remove(); // 【新增】务必一起清除，防止内存泄漏
    }
}