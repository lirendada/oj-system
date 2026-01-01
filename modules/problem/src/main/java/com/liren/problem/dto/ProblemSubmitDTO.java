package com.liren.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "题目提交DTO")
public class ProblemSubmitDTO {
    @Schema(description = "题目ID")
    @NotNull(message = "题目ID不能为空")
    private Long problemId;

    @Schema(description = "编程语言 (Java/C++/Python)")
    @NotBlank(message = "语言不能为空")
    private String language;

    @Schema(description = "代码")
    @NotBlank(message = "代码不能为空")
    private String code;

    @Schema(description = "竞赛ID (可选，平时做题为0或null)")
    private Long contestId;
}
