package com.liren.system.dto;

import com.liren.common.core.request.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 题目查询请求类
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "分页获取题目列表请求参数")
public class ProblemQueryRequest extends PageRequest implements Serializable {

    @Schema(description = "题目 ID (精确搜索)")
    private Long problemId;

    @Schema(description = "标题/内容关键词 (模糊搜索)")
    private String keyword;

    @Schema(description = "难度 (精确搜索: 1-简单 2-中等 3-困难)")
    private Integer difficulty;

    @Schema(description = "标签")
    private List<String> tags;
}