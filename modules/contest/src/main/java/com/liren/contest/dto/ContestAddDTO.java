package com.liren.contest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "比赛新增/修改请求")
public class ContestAddDTO {

    @Schema(description = "比赛ID (修改时必填)")
    private Long contestId;

    @NotBlank(message = "比赛标题不能为空")
    @Schema(description = "比赛标题")
    private String title;

    @Schema(description = "比赛描述")
    private String description;

    @NotNull(message = "开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
