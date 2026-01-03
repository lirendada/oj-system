package com.liren.contest.dto;

import com.liren.common.core.request.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "比赛查询请求")
public class ContestQueryRequest extends PageRequest {

    @Schema(description = "关键词搜索(标题)")
    private String keyword;

    @Schema(description = "状态筛选：0-未开始 1-进行中 2-已结束")
    private Integer status;
}