package com.liren.common.core.constant;

public class Constants {
    public final static String JWT_SECRET = "LirenOJSystemSecretKeyForJwtTokenGeneration2024";
    public final static long TOKEN_EXPIRATION_TIME = 86400000L; // 1天过期时间

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
}
