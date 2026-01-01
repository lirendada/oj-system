package com.liren.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "题目标签VO")
public class ProblemTagVO implements Serializable {

    @Schema(description = "标签ID")
    private Long tagId;

    @Schema(description = "标签名称")
    private String tagName;

    @Schema(description = "标签颜色")
    private String tagColor;
}

