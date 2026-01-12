package com.liren.common.redis;

import cn.hutool.core.date.LocalDateTimeUtil; // 需要 Hutool 5.x+
import com.liren.common.core.constant.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.liren.common.core.constant.Constants.CONTEST_USER_SCORE_DETAIL_PREFIX;

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
        redisTemplate.expire(weeklyKey, Constants.RANK_WEEKLY_EXPIRE_TIME, TimeUnit.DAYS); // 周榜保留 1 周

        // 2.4 月榜 +1 (Key: oj:rank:monthly:202311)
        String monthlyKey = Constants.RANK_MONTHLY_PREFIX + LocalDateTimeUtil.format(now, "yyyyMM");
        redisTemplate.opsForZSet().incrementScore(monthlyKey, userId, Constants.RANK_SUBMIT_ADD_COUNT);
        redisTemplate.expire(monthlyKey, Constants.RANK_MONTHLY_EXPIRE_TIME, TimeUnit.DAYS); // 月榜保留 1 个月

        return true; // 首次ac
    }

    /**
     * 更新比赛排行榜 (按分数模式)
     * @param contestId 比赛ID
     * @param userId 用户ID
     * @param problemId 题目ID
     * @param currentScore 本次提交的得分 (例如 12 分)
     */
    public void updateContestScoreRank(Long contestId, Long userId, Long problemId, Integer currentScore) {
        if (contestId == null || contestId <= 0 || currentScore == null) return;

        // 1. 获取用户在这道题的历史最高分
        // Key: oj:contest:score_detail:{contestId}:{userId}
        String userScoreKey = CONTEST_USER_SCORE_DETAIL_PREFIX + contestId + ":" + userId;

        // 从 Redis Hash 中获取该题目的旧分数
        Object oldScoreObj = redisTemplate.opsForHash().get(userScoreKey, problemId.toString());
        Integer oldScore = oldScoreObj == null ? 0 : Integer.parseInt(oldScoreObj.toString());

        // 2. 只有当“本次得分” > “历史最高分”时，才更新
        if (currentScore > oldScore) {
            // A. 更新这道题的最高分
            redisTemplate.opsForHash().put(userScoreKey, problemId.toString(), currentScore.toString());

            // B. 计算分数差值 (本次涨了多少分)
            int delta = currentScore - oldScore;

            // C. 更新总排行榜 (ZSet)
            // 用户的总分 = 旧总分 + 差值
            String rankKey = Constants.RANK_CONTEST_PREFIX + contestId;
            redisTemplate.opsForZSet().incrementScore(rankKey, userId, delta);

            // 设置过期时间
            redisTemplate.expire(userScoreKey, Constants.CONTEST_USER_SCORE_DETAIL_EXPIRE_TIME, TimeUnit.DAYS);
            redisTemplate.expire(rankKey, Constants.CONTEST_RANK_EXPIRE_TIME, TimeUnit.DAYS);
        }
    }

    /**
     * 获取总榜前 N 名
     */
    public Set<ZSetOperations.TypedTuple<Object>> getTotalRankTopN(int limit) {
        return redisTemplate.opsForZSet().reverseRangeWithScores(Constants.RANK_TOTAL_KEY, 0, limit - 1);
    }

    /**
     * 获取日榜前 N 名
     */
    public Set<ZSetOperations.TypedTuple<Object>> getDailyRankTopN(int limit) {
        String key = Constants.RANK_DAILY_PREFIX + LocalDateTimeUtil.format(LocalDateTime.now(), "yyyyMMdd");
        return redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, limit - 1);
    }

    /**
     * 获取周榜前 N 名
     */
    public Set<ZSetOperations.TypedTuple<Object>> getWeeklyRankTopN(int limit) {
        LocalDateTime now = LocalDateTime.now();
        int year = now.getYear();
        int week = now.get(WeekFields.of(Locale.getDefault()).weekOfYear());
        String weeklyKey = Constants.RANK_WEEKLY_PREFIX + year + String.format("%02d", week);
        return redisTemplate.opsForZSet().reverseRangeWithScores(weeklyKey, 0, limit - 1);
    }

    /**
     * 获取月榜前 N 名
     */
    public Set<ZSetOperations.TypedTuple<Object>> getMonthlyRankTopN(int limit) {
        String key = Constants.RANK_MONTHLY_PREFIX + LocalDateTimeUtil.format(LocalDateTime.now(), "yyyyMM");
        return redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, limit - 1);
    }

    /**
     * 获取用户的总榜分数
     */
    public Integer getUserTotalScore(Long userId) {
        Double score = redisTemplate.opsForZSet().score(Constants.RANK_TOTAL_KEY, userId);
        return score == null ? 0 : score.intValue();
    }

    /**
     * 【新增】获取用户在某场比赛的详细得分
     * @return Map<题目ID(String), 分数(Integer)>
     */
    public Map<Object, Object> getUserScoreDetail(Long contestId, Long userId) {
        String key = Constants.CONTEST_USER_SCORE_DETAIL_PREFIX + contestId + ":" + userId;
        // HGETALL: 获取该用户所有题目的得分
        return redisTemplate.opsForHash().entries(key);
    }
}