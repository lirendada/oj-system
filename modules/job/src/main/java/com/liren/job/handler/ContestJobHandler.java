package com.liren.job.handler;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ContestJobHandler {
    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        String startSql = "UPDATE tb_contest SET status = 1 WHERE status = 0 AND start_time <= NOW()";
        int startedCount = jdbcTemplate.update(startSql);
        if (startedCount > 0) {
            log.info("扫描到 {} 场比赛开始，状态已更新。", startedCount);
            // TODO：进阶优化：这里可以配合 RedisUtil 删除对应的比赛详情缓存
        }

        // 2. 处理 [进行中] -> [已结束]
        // 状态: 1(进行中) -> 2(已结束)
        // 条件: end_time <= NOW()
        String endSql = "UPDATE tb_contest SET status = 2 WHERE status = 1 AND end_time <= NOW()";
        int endedCount = jdbcTemplate.update(endSql);
        if (endedCount > 0) {
            log.info("扫描到 {} 场比赛结束，状态已更新。", endedCount);
        }

        log.info("比赛状态扫描任务执行结束。");
    }
}
