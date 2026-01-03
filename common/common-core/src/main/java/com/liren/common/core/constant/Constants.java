package com.liren.common.core.constant;

public class Constants {
    // ========= JWT配置 =========
    public final static String JWT_SECRET = "LirenOJSystemSecretKeyForJwtTokenGeneration2024";
    public final static long TOKEN_EXPIRATION_TIME = 86400000L; // 1天过期时间


    // ========= MQ配置 =========
    /**
     * 判题队列名称
     */
    public static final String JUDGE_QUEUE = "oj.judge.queue";

    /**
     * 判题交换机 (Direct模式)
     */
    public static final String JUDGE_EXCHANGE = "oj.judge.exchange";

    /**
     * 路由键
     */
    public static final String JUDGE_ROUTING_KEY = "oj.judge";



    // ========= 沙箱配置 =========
    /**
     * 判题机镜像名称
     */
    public static final String SANDBOX_IMAGE = "liren-oj-sandbox:v1";

    /**
     * 沙箱运行超时时间 (毫秒)
     * 建议定义为 Long，方便直接使用
     */
    public static final Long SANDBOX_TIME_OUT = 10000L;

    /**
     * 沙箱内存限制 (字节)
     * 100MB = 100 * 1000 * 1000
     */
    public static final Long SANDBOX_MEMORY_LIMIT = 100 * 1000 * 1000L;

    /**
     * 沙箱 CPU 限制 (核数)
     */
    public static final Long SANDBOX_CPU_COUNT = 1L;
}
