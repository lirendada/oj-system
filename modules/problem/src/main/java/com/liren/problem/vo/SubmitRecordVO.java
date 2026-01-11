package com.liren.problem.vo;

import com.liren.common.core.base.BaseTimeEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "提交记录VO")
public class SubmitRecordVO extends BaseTimeEntity {
    @Schema(description = "提交记录ID")
    private Long submitId;

    @Schema(description = "题目ID")
    private Long problemId;

    @Schema(description = "比赛ID")
    private Long contestId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "提交代码(非本人查看时需脱敏)")
    private String code;

    @Schema(description = "用户选择的编程语言")
    private String language;

    @Schema(description = "判题状态")
    private Integer status;

    @Schema(description = "判题结果")
    private Integer judgeResult;

    @Schema(description = "最大耗时（ms）")
    private Integer timeCost;

    @Schema(description = "最大内存（KB）")
    private Integer memoryCost;

    @Schema(description = "错误信息 (非本人查看时需脱敏)")
    private String errorMessage;

    @Schema(description = "通过用例数")
    private Integer passCaseCount;

    @Schema(description = "总用例数")
    private Integer totalCaseCount;
}
