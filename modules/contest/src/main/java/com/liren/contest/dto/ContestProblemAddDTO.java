package com.liren.contest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "向比赛添加题目请求")
public class ContestProblemAddDTO {
    @NotNull(message = "比赛ID不能为空")
    private Long contestId;

    @NotNull(message = "题目ID不能为空")
    private Long problemId;

    @NotBlank(message = "展示序号不能为空")
    @Schema(description = "例如 A, B, C")
    private String displayId;
}