package com.liren.problem.dto;

import com.liren.common.core.request.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "获取提交记录列表请求参数")
public class ProblemSubmitQueryRequest extends PageRequest {

    @Schema(description = "题目ID")
    private Long problemId; // 不判断是否为空，因为可能是在个人中心查看所有的提交记录
}