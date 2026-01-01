package com.liren.problem.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "题目详情VO")
public class ProblemDetailVO extends ProblemVO {
    @Schema(description = "题目描述(Markdown)")
    private String description;

    @Schema(description = "输入描述")
    private String inputDescription;

    @Schema(description = "输出描述")
    private String outputDescription;

    @Schema(description = "时间限制ms")
    private Integer timeLimit;

    @Schema(description = "空间限制MB")
    private Integer memoryLimit;

    @Schema(description = "栈空间限制MB")
    private Integer stackLimit;

    @Schema(description = "样例输入")
    private String sampleInput;

    @Schema(description = "样例输出")
    private String sampleOutput;

    @Schema(description = "提示")
    private String hint;

    @Schema(description = "来源")
    private String source;
}
