package com.liren.job.handler;

import com.liren.common.core.constant.Constants;
import com.liren.common.redis.RedisUtil;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ContestJobHandler {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisUtil redisUtil;

    /**
     * 自动更新比赛状态 Job
     * 频率建议：每 30 秒或 1 分钟执行一次
     */
    @XxlJob("contestStatusHandler")
    public void contestStatusHandler() {
        log.info("开始执行比赛状态扫描任务...");

        // 1. 处理 [未开始] -> [进行中]
        // 状态: 0(未开始) -> 1(进行中)
        // 条件: start_time <= NOW()
        String selectStartSql = "SELECT contest_id FROM tb_contest WHERE status = 0 AND start_time <= NOW()";
        List<Long> startContestIds = jdbcTemplate.queryForList(selectStartSql, Long.class);

        if (!CollectionUtils.isEmpty(startContestIds)) {
            // B. 执行更新
            String updateStartSql = "UPDATE tb_contest SET status = 1 WHERE contest_id = ?";
            // 批量更新性能更好
            List<Object[]> batchArgs = new ArrayList<>();
            for (Long id : startContestIds) {
                batchArgs.add(new Object[]{id});
            }
            jdbcTemplate.batchUpdate(updateStartSql, batchArgs);

            // C. 删除缓存
            for (Long id : startContestIds) {
                String key = Constants.CONTEST_DETAIL_CACHE_PREFIX + id;
                redisUtil.del(key);
            }
            log.info("比赛开始扫描：更新并清理缓存 {} 条，ID列表：{}", startContestIds.size(), startContestIds);
        }

        // 2. 处理 [进行中] -> [已结束]
        // 状态: 1(进行中) -> 2(已结束)
        // 条件: end_time <= NOW()
        String selectEndSql = "SELECT contest_id FROM tb_contest WHERE status = 1 AND end_time <= NOW()";
        List<Long> endContestIds = jdbcTemplate.queryForList(selectEndSql, Long.class);

        if (!CollectionUtils.isEmpty(endContestIds)) {
            // 更新 DB
            String updateEndSql = "UPDATE tb_contest SET status = 2 WHERE contest_id = ?";
            List<Object[]> batchArgs = new ArrayList<>();
            for (Long id : endContestIds) {
                batchArgs.add(new Object[]{id});
            }
            jdbcTemplate.batchUpdate(updateEndSql, batchArgs);

            // 删除缓存
            for (Long id : endContestIds) {
                String key = Constants.CONTEST_DETAIL_CACHE_PREFIX + id;
                redisUtil.del(key);
            }
            log.info("比赛结束扫描：更新并清理缓存 {} 条，ID列表：{}", endContestIds.size(), endContestIds);
        }

        log.info("比赛状态扫描任务执行结束。");
    }
}
