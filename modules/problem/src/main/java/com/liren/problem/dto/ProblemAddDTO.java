package com.liren.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "题目信息传输对象")
public class ProblemAddDTO {

    @Schema(description = "题目ID (更新时必填)")
//    // 只有在 Update 分组下，才校验 ID 不为空
//    @NotNull(message = "修改时ID不能为空", groups = UpdateGroup.class)
//    // 只有在 Add 分组下，才校验 ID 必须为空
//    @Null(message = "新增时ID必须为空", groups = AddGroup.class)
    private Long problemId;

    @NotBlank(message = "题目标题不能为空")
    @Schema(description = "题目标题")
    private String title;

    @NotNull(message = "难度不能为空")
    @Schema(description = "难度：1-简单 2-中等 3-困难")
    private Integer difficulty;

    @NotBlank(message = "题目描述不能为空")
    @Schema(description = "题目描述")
    private String description;

    @Schema(description = "输入描述")
    private String inputDescription;

    @Schema(description = "输出描述")
    private String outputDescription;

    @NotNull(message = "时间限制不能为空")
    @Schema(description = "时间限制(ms)")
    private Integer timeLimit;

    @NotNull(message = "内存限制不能为空")
    @Schema(description = "内存限制(MB)")
    private Integer memoryLimit;

    @NotNull(message = "栈限制不能为空")
    @Schema(description = "栈限制(MB)")
    private Integer stackLimit;

    @Schema(description = "样例输入")
    private String sampleInput;

    @Schema(description = "样例输出")
    private String sampleOutput;

    @Schema(description = "提示")
    private String hint;

    @Schema(description = "来源")
    private String source;

    @Schema(description = "关联的标签ID列表")
    private List<Long> tagIds;
}