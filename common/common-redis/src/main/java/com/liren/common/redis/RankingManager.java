package com.liren.common.redis;

import cn.hutool.core.date.LocalDateTimeUtil; // 需要 Hutool 5.x+
import com.liren.common.core.constant.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 排行榜管理器 (支持 日榜/周榜/月榜/总榜)
 * 优化：使用 LocalDateTime + 解决并发重复加分问题
 */
@Component
public class RankingManager {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 用户 AC 一道题（自动处理去重 + 更新所有榜单）
     */
    public boolean userAcProblem(Long userId, Long problemId) {
        // 1. 【原子操作去重】
        // SADD命令：如果元素已存在返回0，不存在返回1。利用返回值判断是否是第一次AC。
        String userSolvedKey = Constants.USER_SOLVED_KEY_PREFIX + userId;
        Long addedCount = redisTemplate.opsForSet().add(userSolvedKey, problemId);

        // 如果返回值是 0 (或 null)，说明之前已经 AC 过了，直接返回，解决并发问题
        if (addedCount == null || addedCount == 0) {
            return false; // 非首次ac
        }

        // 2. 更新各个维度的排行榜
        LocalDateTime now = LocalDateTime.now();

        // 2.1 总榜 +1
        redisTemplate.opsForZSet().incrementScore(Constants.RANK_TOTAL_KEY, userId, Constants.RANK_SUBMIT_ADD_COUNT);

        // 2.2 日榜 +1 (Key: oj:rank:daily:20231120)
        String dailyKey = Constants.RANK_DAILY_PREFIX + LocalDateTimeUtil.format(now, "yyyyMMdd");
        redisTemplate.opsForZSet().incrementScore(dailyKey, userId, Constants.RANK_SUBMIT_ADD_COUNT);
        redisTemplate.expire(dailyKey, Constants.RANK_DAILY_EXPIRE_TIME, TimeUnit.DAYS); // 日榜保留 3 天

        // 2.3 周榜 +1 (Key: oj:rank:weekly:202347)
        // 使用 ISO-8601 标准或者特定 Locale 计算周数
        // 简单做法：yyyy + 周数
        int year = now.getYear();
        // 获取当前是一年中的第几周 (注意：WeekFields.ISO 是国际标准，周一为第一天)
        int week = now.get(WeekFields.of(Locale.getDefault()).weekOfYear());
        String weeklyKey = Constants.RANK_WEEKLY_PREFIX + year + String.format("%02d", week);
        redisTemplate.opsForZSet().incrementScore(weeklyKey, userId, Constants.RANK_SUBMIT_ADD_COUNT);

        // 2.4 月榜 +1 (Key: oj:rank:monthly:202311)
        String monthlyKey = Constants.RANK_MONTHLY_PREFIX + LocalDateTimeUtil.format(now, "yyyyMM");
        redisTemplate.opsForZSet().incrementScore(monthlyKey, userId, Constants.RANK_SUBMIT_ADD_COUNT);

        return true; // 首次ac
    }

    /**
     * 获取总榜前 N 名
     */
    public Set<Object> getTotalRankTopN(int limit) {
        return redisTemplate.opsForZSet().reverseRange(Constants.RANK_TOTAL_KEY, 0, limit - 1);
    }

    /**
     * 获取日榜前 N 名
     */
    public Set<Object> getDailyRankTopN(int limit) {
        String key = Constants.RANK_DAILY_PREFIX + LocalDateTimeUtil.format(LocalDateTime.now(), "yyyyMMdd");
        return redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);
    }

    /**
     * 获取周榜前 N 名
     */
    public Set<Object> getWeeklyRankTopN(int limit) {
        LocalDateTime now = LocalDateTime.now();
        int year = now.getYear();
        int week = now.get(WeekFields.of(Locale.getDefault()).weekOfYear());
        String weeklyKey = Constants.RANK_WEEKLY_PREFIX + year + String.format("%02d", week);
        return redisTemplate.opsForZSet().reverseRange(weeklyKey, 0, limit - 1);
    }

    /**
     * 获取月榜前 N 名
     */
    public Set<Object> getMonthlyRankTopN(int limit) {
        String key = Constants.RANK_MONTHLY_PREFIX + LocalDateTimeUtil.format(LocalDateTime.now(), "yyyyMM");
        return redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);
    }

    /**
     * 获取用户的总榜分数
     */
    public Integer getUserTotalScore(Long userId) {
        Double score = redisTemplate.opsForZSet().score(Constants.RANK_TOTAL_KEY, userId);
        return score == null ? 0 : score.intValue();
    }
}