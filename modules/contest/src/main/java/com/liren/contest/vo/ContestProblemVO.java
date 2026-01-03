package com.liren.contest.vo;

import lombok.Data;

@Data
public class ContestProblemVO {
    private Long id;          // 关联记录的ID
    private Long contestId;
    private Long problemId;   // 原始题目ID
    private String displayId; // A, B, C
    private String title;     // 题目标题 (来自 oj-problem)
    private Integer difficulty;
}