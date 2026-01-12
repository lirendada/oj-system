package com.liren.contest.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.Map;

@Data
public class ContestRankVO {
    private Long userId;
    private String nickname;
    private String avatar;

    private Integer rank;       // 排名
    private Integer totalScore; // 总得分

    // 【新增】每道题的得分详情
    // Key: problemId, Value: score (e.g., 1001 -> 25)
    private Map<Long, Integer> problemScores;
}