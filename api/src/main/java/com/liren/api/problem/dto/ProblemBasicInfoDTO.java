package com.liren.api.problem.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用于跨服务传输题目的简要信息
 */
@Data
public class ProblemBasicInfoDTO implements Serializable {
    private Long problemId;
    private String title;
    private Integer difficulty;
    private Integer timeLimit;
    private Integer memoryLimit;
}