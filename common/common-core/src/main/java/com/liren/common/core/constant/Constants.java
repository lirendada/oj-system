package com.liren.common.core.constant;

public class Constants {
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



    // ========= 排行榜 =========
    public static final String USER_SOLVED_KEY_PREFIX = "oj:solved:"; // 记录用户已解决题目的 Set Key 前缀: oj:solved:{userId}

    // 全局排行榜
    public static final String RANK_TOTAL_KEY = "oj:rank:total";
    public static final String RANK_DAILY_PREFIX = "oj:rank:daily:";   // + yyyyMMdd
    public static final String RANK_WEEKLY_PREFIX = "oj:rank:weekly:"; // + yyyyw (年份+周数)
    public static final String RANK_MONTHLY_PREFIX = "oj:rank:monthly:"; // + yyyyMM

    public static final Long RANK_DAILY_EXPIRE_TIME = 3l; // 日排行榜过期时间

    public static final Long RANK_SUBMIT_ADD_COUNT = 1l; // 每提交一次增加的分数

    // 比赛排行榜
    public static final String CONTEST_USER_SCORE_DETAIL_PREFIX = "oj:contest:score_detail:"; // 比赛用户各题得分详情 Hash 前缀
    public static final String RANK_CONTEST_PREFIX = "oj:rank:contest:"; // 比赛排行榜 ZSet 前缀
    public static final Long CONTEST_USER_SCORE_DETAIL_EXPIRE_TIME = 30l; // 用户各题得分详情 Hash 过期时间
    public static final Long CONTEST_RANK_EXPIRE_TIME = 30l; // 比赛排行榜过期时间


    // 比赛每道题的分数
    public static final Integer CONTEST_QUESTION_SCORE = 25; // 每道题的分数
}
